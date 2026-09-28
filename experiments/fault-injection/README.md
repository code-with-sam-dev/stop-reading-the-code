# The double payout, reproduced

Reading the agent's code raised one concern: the provider is called inside the database
transaction. `CommitFailsAfterPayoutTest` makes the database reject the first "mark as
refunded" update, standing in for any failure between the payout and the commit.

Result on the agent's change: the first approval pays out and fails; the refund still
says PENDING; the retry pays out again. Two payouts for one refund.

Note on the fake provider: the service's `RecordingRefundProvider` writes its record in
the same transaction, so a rollback erases the evidence. A real card network does not
roll back. This test counts payouts in memory, outside the transaction.

Run it against the agent's change:

    git worktree add /tmp/fi agent-run
    cp experiments/fault-injection/CommitFailsAfterPayoutTest.java /tmp/fi/service/src/test/java/dev/example/payments/
    cd /tmp/fi/service && ./mvnw -Dtest=CommitFailsAfterPayoutTest test
