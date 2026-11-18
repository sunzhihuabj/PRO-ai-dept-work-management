package com.dept.mgmt.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户名称字典查询（M5 脱敏字典源：主体名 + 别名去重合并）
 *
 * @author dept-mgmt
 */
@Mapper
public interface CustomerDictMapper {

    @Select("SELECT customer_name FROM mdm_customer WHERE customer_name IS NOT NULL AND customer_name != '' "
            + "UNION SELECT alias1 FROM mdm_customer WHERE alias1 IS NOT NULL AND alias1 != '' "
            + "UNION SELECT alias2 FROM mdm_customer WHERE alias2 IS NOT NULL AND alias2 != '' "
            + "LIMIT 2000")
    List<String> selectAllNames();
}
