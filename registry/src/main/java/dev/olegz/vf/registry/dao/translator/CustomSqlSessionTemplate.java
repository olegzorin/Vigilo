package dev.olegz.vf.registry.dao.translator;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;

/**
 * Use CustomSQLErrorCodesTranslator in MyBatis SqlSessionTemplate
 */
public class CustomSqlSessionTemplate extends SqlSessionTemplate {
    public CustomSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        super(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType(),
            new CustomExceptionTranslator(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()));
    }
}
