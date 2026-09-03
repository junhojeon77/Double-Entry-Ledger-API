create or replace function reject_mutation() return trigger as $$
begin
    raise exception 'Posting rows are append-only (attempted %)', tg_op;
end $$ language plpgsql;

create trigger posting_append_only
    before update or delete on postings
    for each row execute procedure reject_mutation();

    