-- V2: the books must balance, enforced by the database itself.
--
-- WHAT IT DOES: after postings are inserted, sums them per transfer with
-- DEBIT counting negative and CREDIT positive. Anything but zero aborts.
--
-- WHY IN SQL AND NOT JAVA: this is layer two. PostingEngine already guarantees
-- balanced pairs in application code; this trigger assumes that code will
-- eventually have a bug. A ledger that can be unbalanced by a bad deploy is
-- not a ledger. Every guarantee in this project has an app layer and a DB layer.
--
-- DEFERRABLE INITIALLY DEFERRED is load-bearing: the two rows of a pair are
-- inserted one at a time, so after the first insert the sum is deliberately
-- non-zero. Deferring the check to COMMIT lets the pair complete first.
-- CONSEQUENCE FOR TESTS: this does not fire on flush(). A test that expects a
-- violation must actually commit, or it will pass while proving nothing.

create or replace function assert_transfer_balanced() returns trigger as $$
declare net bigint;

begin 
    select coalesce(sum(case when direction = 'DEBIT' then -amount_minor
                        else amount_minor end), 0) into net
    from posting where transfer_id = new.transfer_id;

    if net <> 0 then 
        raise exception 'Unbalanced transfer %: net = %', new.transfer_id, net;
    end if;
    return null;
end $$ language plpgsql;

create constraint trigger posting_must_balance
    after insert on posting
    deferrable initially deferred
    for each row execute procedure assert_transfer_balanced();