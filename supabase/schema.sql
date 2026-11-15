-- ANAS Control starter schema.
-- Apply in Supabase SQL Editor. RLS is enabled on every user-owned table.
create extension if not exists pgcrypto;

create table if not exists public.families (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  display_name text not null,
  platform text not null default 'android',
  enrollment_mode text not null check (enrollment_mode in ('standard','managed')),
  status text not null default 'pending' check (status in ('pending','active','revoked')),
  created_at timestamptz not null default now()
);

create table if not exists public.device_links (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  parent_user_id uuid not null references auth.users(id) on delete cascade,
  relationship text not null default 'parent',
  created_at timestamptz not null default now(),
  unique(device_id, parent_user_id)
);

create table if not exists public.consent_records (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  permission_key text not null,
  granted boolean not null,
  disclosure_version text not null,
  recorded_at timestamptz not null default now()
);

create table if not exists public.audit_events (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  actor_user_id uuid references auth.users(id) on delete set null,
  device_id uuid references public.devices(id) on delete set null,
  event_type text not null,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

alter table public.families enable row level security;
alter table public.devices enable row level security;
alter table public.device_links enable row level security;
alter table public.consent_records enable row level security;
alter table public.audit_events enable row level security;

-- Owner can manage their own family.
create policy "family owner can read family"
on public.families for select to authenticated
using (owner_user_id = auth.uid());

create policy "user can create family"
on public.families for insert to authenticated
with check (owner_user_id = auth.uid());

create policy "family owner can read devices"
on public.devices for select to authenticated
using (exists (
  select 1 from public.families f
  where f.id = family_id and f.owner_user_id = auth.uid()
));

create policy "family owner can create devices"
on public.devices for insert to authenticated
with check (exists (
  select 1 from public.families f
  where f.id = family_id and f.owner_user_id = auth.uid()
));

create policy "parent can read own links"
on public.device_links for select to authenticated
using (parent_user_id = auth.uid());

create policy "parent can read consent records"
on public.consent_records for select to authenticated
using (exists (
  select 1 from public.devices d
  join public.families f on f.id = d.family_id
  where d.id = device_id and f.owner_user_id = auth.uid()
));

create policy "parent can read audit events"
on public.audit_events for select to authenticated
using (exists (
  select 1 from public.families f
  where f.id = family_id and f.owner_user_id = auth.uid()
));

-- NOTE: device-side writes must be added through narrowly scoped Edge Functions
-- or authenticated device credentials. Never expose service_role keys in APKs.
