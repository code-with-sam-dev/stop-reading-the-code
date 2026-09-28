# Refunds: product specification

Frozen before any code was written. The implementation and its tests are checked
against this document, not the other way round.

## Who is who

- A **customer** is identified by the `X-Customer-Id` header.
- A **staff member** is identified by the `X-Staff-Id` header.
- A customer may only see and act on their own payments and refunds. Anything else
  answers **404**, never 403, so nobody can probe which ids exist.

## 1. A customer asks for a refund

`POST /payments/{paymentId}/refunds` as the customer who made the payment.

- The refund is for the **full amount** of the payment.
- It is created as `PENDING`. Nothing is paid yet.
- **The refund window depends on how the customer paid:**
  - `CARD`: within **14 days** of the payment being captured.
  - `BANK_TRANSFER`: within **30 days** of the payment being captured.
  - Exactly on the last day is still inside the window.
  - Outside the window the answer is **422** and no refund is created.
- A payment can have at most **one** refund that is `PENDING` or `REFUNDED`. A second
  request answers **409**.
- Answers **201** with the refund: `id`, `paymentId`, `amountCents`, `status`.

## 2. Staff approve a refund

`POST /refunds/{refundId}/approve` as a staff member.

- Calls the payment provider (`RefundProvider.refund`) and marks the refund `REFUNDED`.
- **Money moves at most once per refund.** Approving a refund that is already
  `REFUNDED` changes nothing and does not call the provider again. This must hold even
  when two staff members approve the same refund at the same moment.
- Answers **200** with the refund.

## 3. A customer lists their refunds

`GET /refunds` as the customer.

- Only that customer's refunds, newest first.
- Each entry includes the **merchant name** of the payment it refunds.
- A customer may have hundreds of refunds. The listing must not issue one database
  query per refund.

## 4. A customer downloads a refund receipt

`GET /refunds/{refundId}/receipt` as the customer.

- Only for the customer's own refund; anyone else gets **404**.
- Contains: refund id, payment id, merchant name, amount, status.
