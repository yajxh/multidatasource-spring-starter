package chy.frame.multidatasourcespringstarter.core;

import chy.frame.multidatasourcespringstarter.annotation.TransactionMulti;
import chy.frame.multidatasourcespringstarter.enums.TransactionTypeEnum;
import org.apache.ibatis.session.SqlSessionException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;


import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Aspect
public class MultiTransactionManagerAop {


    @Autowired
    DataSourceRouting dataSourceRouting;


    @Pointcut("@annotation(chy.frame.multidatasourcespringstarter.annotation.TransactionMulti)")
    public void annotationPointcut(){}


    @Around("annotationPointcut()")
    public void roundExecute(ProceedingJoinPoint joinpoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinpoint.getSignature();
        Method method = methodSignature.getMethod();
        TransactionMulti annotation = method.getAnnotation(TransactionMulti.class);

        String[] values = annotation.value();
        int transactionType = annotation.transactionType();
        //把涉及到的连接绑定到线程上,开启事务,关闭自动提交
        begin(values,transactionType);
        //正真执行了 方法
        joinpoint.proceed();
        //commit
        dataSourceRouting.doCommit();
    }

    @AfterThrowing(pointcut = "annotationPointcut()",throwing = "e")
    public void handleThrowing(JoinPoint joinPoint, Exception e) {//controller类抛出的异常在这边捕获
        try {
            dataSourceRouting.rollback();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }



    private void begin(String[] values,int transactionType) throws SQLException {
        List<Connection> result = new ArrayList<>();
        for (String value : values) {
            DataSource dataSource = dataSourceRouting.getDataSource(value);
            if(dataSource == null){
                continue;
            }
            Connection connection = dataSource.getConnection();
            prepareTransactionalConnection(connection,transactionType);
            connectBegin(connection);
            //绑定到线程上面
            dataSourceRouting.bindConnection(value,connection);
        }
    }

    /**
     * 开启事物的一些准本工作
     */
    private void connectBegin(Connection connection) throws SQLException {
        if(connection!=null){
            try {
                if(connection.getAutoCommit()){
                    connection.setAutoCommit(false);
                }
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }


    /**
     * 设置隔离级别
     * @param con
     * @throws SQLException
     */
    protected void prepareTransactionalConnection(Connection con,int transactionType)
            throws SQLException {
        if (TransactionTypeEnum.isNotDefined(transactionType)){
            throw new SqlSessionException("当前事物隔离级别未被定义");
        }
        con.setTransactionIsolation(transactionType);
    }


}
