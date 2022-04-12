CREATE TABLE IF NOT EXISTS member(
  account   varchar(10) primary key,
  username  varchar(16) not null,
  passwd    varchar(20) not null,
  enabled   varchar(1) default 'Y' check(enabled='Y' or enabled='N'), 
  birthday  date default CURRENT_DATE()
);

CREATE TABLE IF NOT EXISTS authorities(
  aid SERIAL primary key,
  account varchar(10) references member(account) on update cascade on delete cascade,
  authority varchar(50) not null
);
create unique index IF NOT EXISTS authorities_idx on authorities(account,authority);

SET TRACE_LEVEL_FILE 2;

INSERT INTO member(account,username,passwd)
SELECT 'root','Administrator','webflux'
WHERE NOT EXISTS(SELECT 1 FROM member where account='root');


INSERT INTO authorities(account,authority) select 'root','ROLE_ADMIN'
 where not exists(select 1 from authorities where account='root' and authority='ROLE_ADMIN');

INSERT INTO member(account,username,passwd)
SELECT 'user','CommonUser','helloWorld'
WHERE NOT EXISTS(SELECT 1 FROM member where account='user');

INSERT INTO member(account,username,passwd)
SELECT 'nobody','無人識君','nobody'
WHERE NOT EXISTS(SELECT 1 FROM member where account='nobody');

INSERT INTO authorities(account,authority) select 'nobody','ROLE_WEBSOCKET'
 where not exists(select 1 from authorities where account='nobody' and authority='ROLE_WEBSOCKET');