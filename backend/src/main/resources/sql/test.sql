create database if not exists test;
use test;
-- 1. 用户表（主表）
CREATE TABLE if not exists `users` (
 `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
 `name` VARCHAR(20) NOT NULL COMMENT '用户姓名',
 `phone` VARCHAR(11) UNIQUE COMMENT '手机号',
 `register_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) COMMENT '用户信息表';

-- 2. 订单表（从表，关联用户表）
CREATE TABLE if not exists `orders` (
  `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
  `user_id` INT NOT NULL COMMENT '下单用户ID（关联users表）',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `status` TINYINT DEFAULT 0 COMMENT '订单状态：0待支付 1已支付 2已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
-- 外键约束，保证用户ID必须存在
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) COMMENT '订单信息表';

SELECT
    u.`name` AS 用户名,
    u.`phone` AS 手机号,
    o.`order_no` AS 订单号,
    o.`amount` AS 订单金额,
    o.`create_time` AS 下单时间
FROM `users` u
         INNER JOIN `orders` o
                    ON u.`id` = o.`user_id`  -- 关联两张表的核心条件
WHERE o.`status` = 1  -- 只查已支付的订单
ORDER BY o.`create_time` DESC;


CREATE TABLE if not exists demo_all_type (
-- 1. 整数类型
   id TINYINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '微整型ID',
   age SMALLINT DEFAULT 0 COMMENT '小整型年龄',
   user_int INT NOT NULL DEFAULT 0 COMMENT '普通整型',
   big_num BIGINT DEFAULT 0 COMMENT '大整型',

-- 2. 高精度小数 / 浮点
   price DECIMAL(10,2) DEFAULT 0.00 COMMENT '定点金额(推荐财务)',
   score FLOAT DEFAULT 0.0 COMMENT '单精度浮点',
   weight DOUBLE DEFAULT 0.0 COMMENT '双精度浮点',
   num_numeric NUMERIC(12,3) DEFAULT 0.000 COMMENT '标准数值型',

-- 3. 字符串类型
   username CHAR(20) NOT NULL DEFAULT '' COMMENT '固定长度字符串',
   nickname VARCHAR(50) NOT NULL DEFAULT '' COMMENT '可变长度字符串',
   text_info TEXT COMMENT '长文本(非二进制)',
   medium_text MEDIUMTEXT COMMENT '中等长文本',
   long_text LONGTEXT COMMENT '超长文本',

-- 4. 日期时间类型
   create_time DATE COMMENT '日期 年月日',
   birth_time TIME COMMENT '时间 时分秒',
   update_datetime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '日期时间',
   ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
   year_time YEAR COMMENT '年份',

-- 5. 枚举 / 集合
   sex ENUM('男','女','未知') DEFAULT '未知' COMMENT '枚举单选',
   tags SET('游戏','运动','音乐') COMMENT '集合多选',

-- 6. 布尔 / 逻辑
   is_delete BOOLEAN DEFAULT FALSE COMMENT '布尔逻辑位',
   status TINYINT(1) DEFAULT 1 COMMENT '逻辑状态位(常用替代布尔)',

-- 7. JSON 类型（业务常用）
   json_data JSON COMMENT 'JSON结构化数据',

   PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MySQL全字段类型示例表(不含BLOB/二进制字节类)';