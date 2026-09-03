create table account (
    id uuid primary key,
    account_number text not null unique,
    owner_name text not null,
    currency char(3) not null,
    status text not null default 'ACTIVE',
    balance_minor bigint not null default 0,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    overdraft_limit_minor bigint not null default 0 check (overdraft_limit_minor >= 0),
    constraint balance_above_overdraft_limit check (balance_minor >= - overdraft_limit_minor)
);
create table transfer (
    id uuid primary key,
    idempotency_key text not null unique,
    request_hash text not null,
    status text not null, 
    source_account_id uuid not null references account(id),
    target_account_id uuid not null references account(id),
    amount_minor bigint not null check(amount_minor > 0),
    currency char(3) not null,
    failure_reason text,
    created_at timestamptz not null default now(),
    posted_at timestamptz,
    constraint no_self_transfer check (source_account_id <> target_account_id)
);
create table posting (
    id bigint generated always as identity primary key,
    transfer_id uuid not null references transfer(id),
    account_id uuid not null references account(id),
    direction text not null check(direction in ('DEBIT', 'CREDIT')),
    amount_minor bigint not null check(amount_minor > 0),
    currency char(3) not null,
    created_at timestamptz not null default now()
);

create index posting_account_idx on posting(account_id, created_at);
create index posting_transfer_idx on posting(transfer_id, created_at);