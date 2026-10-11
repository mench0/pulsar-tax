-- Сессии хранятся вне базы (SessionStore: Caffeine, в будущем Redis) со временем жизни pulsar.session.ttl.
drop table sessions;
