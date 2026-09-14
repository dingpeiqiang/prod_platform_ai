-- =============================================================
-- 销售品配置上线脚本（模拟生成）
-- product_id=${product_id} offer_id=${offer_id}
-- offer_name=${offer_name}
-- 生成时间=${generated_at}
-- 说明：本脚本由产销品加载AI应用配置落地环节自动生成，仅作演示产物，
--       执行前须由人工复核；表结构对齐 CRM/billing 落库样例风格。
-- =============================================================

/*run@crm 定价基本信息PD_GOODSPRC_DICT*/
insert into PD_GOODSPRC_DICT (GOODS_ID, GOODS_NAME, PRC_ID, PRC_NAME, CLASS_ID, MONTH_FEE, EFF_DATE, EXP_DATE, STATE, STATE_TIME, OP_TIME)
select '${goods_id}', '${offer_name}', '${prc_id}', '${offer_name}定价', '${class_id}', ${month_fee}, sysdate, ${exp_date}, '1', sysdate, sysdate from dual;
insert into PD_GOODSCLASS_REL (GOODS_ID, CLASS_ID, STATE, STATE_TIME)
select '${goods_id}', '${class_id}', '1', sysdate from dual;
insert into PD_GOODSOPCODE_REL (GOODS_ID, OPCODE, STATE, STATE_TIME)
select '${goods_id}', '${product_id}', '1', sysdate from dual;
insert into PD_GOODSRELEASE_DICT (GOODS_ID, RELEASE_VER, RELEASE_DESC, STATE, STATE_TIME)
select '${goods_id}', '${release_ver}', '${offer_name} 上线发布', '1', sysdate from dual;

/*run@billing 优惠/累计/提醒配置*/
insert into FAV_INDEX (FAV_ID, FAV_NAME, FAV_TYPE, GOODS_ID, EFF_DATE, EXP_DATE, STATE, STATE_TIME)
select Fun_getFavType_seq, '${offer_name}优惠', 'D', '${goods_id}', sysdate, ${exp_date}, '1', sysdate from dual;
insert into CUMULATE_VALUE_CTRL (CUMULATE_ID, CUMULATE_NAME, CUMULATE_TYPE, UPPER_VALUE, EFF_DATE, EXP_DATE, STATE)
select distinct Fun_getFavType_seq, '${offer_name}资源累计', 'F', '${flow}+${voice}+${sms}', sysdate, ${exp_date}, '1' from dual;
insert into VOICEFAV_CFEE_PLAN (PLAN_ID, PLAN_NAME, FEE_TYPE, FEE_VALUE, GOODS_ID, EFF_DATE, EXP_DATE, STATE)
select Fun_getFavType_seq, '${offer_name}套外资费', 'D', '${out_flow}/${out_voice}/${out_sms}', '${goods_id}', sysdate, ${exp_date}, '1' from dual;
insert into PRICING_COMBINE (COMBINE_ID, COMBINE_NAME, PLAN_ID, GOODS_ID, PRC_ID, STATE, STATE_TIME)
select Fun_getFavType_seq, '${offer_name}组合定价', Fun_getFavType_seq, '${goods_id}', '${prc_id}', '1', sysdate from dual;
insert into REMIND_ITEM_PROPERTY (ITEM_ID, ITEM_NAME, REMIND_TYPE, GOODS_ID, STATE, STATE_TIME)
select Fun_getFavType_seq, '${offer_name}用量提醒', '1', '${goods_id}', '1', sysdate from dual;
insert into REMIND_GROUP_MEMBER (GROUP_ID, ITEM_ID, GOODS_ID, STATE, STATE_TIME)
select distinct Fun_getFavType_seq, Fun_getFavType_seq, '${goods_id}', '1', sysdate from dual;

-- 脚本结束 stamp=${stamp}
