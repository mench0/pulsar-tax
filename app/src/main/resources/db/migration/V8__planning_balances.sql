-- Фактически отложенная на налоги сумма и остаток денег на счетах, введённые пользователем, и на какую дату.
alter table businesses
    add column reserve_balance    numeric(16, 2),
    add column cash_balance       numeric(16, 2),
    add column balances_as_of     date;
