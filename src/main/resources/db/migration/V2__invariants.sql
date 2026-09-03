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