-- V3: posted history is append-only.
--
-- WHAT IT DOES: any UPDATE or DELETE on posting raises an exception. Rows go in
-- and never change. This is a BEFORE trigger, so unlike V2 it fires immediately
-- on flush, not at commit.
--
-- WHY: real ledgers never edit history. A mistake is corrected by posting a
-- compensating reversal — a new pair of postings that cancels the old one — so
-- the audit trail shows both the error and the correction. Editing a row would
-- destroy the evidence that the error ever happened.
--
-- FUTURE: layer two is a GRANT, not a trigger. The application role should hold
-- only SELECT + INSERT on posting, so a compromised app *cannot* issue the
-- UPDATE at all. The trigger catches bugs; the missing privilege catches attackers.

create or replace function reject_mutation() returns trigger as $$
begin
    raise exception 'Posting rows are append-only (attempted %)', tg_op;
end $$ language plpgsql;

create trigger posting_append_only
    before update or delete on posting
    for each row execute procedure reject_mutation();

