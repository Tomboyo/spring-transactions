package com.github.tomboyo.springtransactions;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ApplicationRunner runner(Example example) {
        return args -> {
            try {
                example.run1();
            } finally {
                example.run2();
            }
        };
    }

    @Component
    public class Example {
        private final MyEntityRepository repo;
        private final PlatformTransactionManager ptm;
        private final Environment env;

        @Autowired
        public Example(Environment env, MyEntityRepository repo, PlatformTransactionManager ptm) {
            this.env = env;
            this.repo = repo;
            this.ptm = ptm;
        }

        @Transactional
        // We can start a container-managed transaction using the typical annotation.
        public void run1() {
            repo.save(new MyEntity());
            System.out.println("Outer-0) Entities: " + repo.count());

            // In the same function, we can use a transaction template to start another
            // transaction. In this case we'll pause the outer transaction and start a new
            // one with read_committed isolation level. It won't see the entity we created
            // above.
            var template = new TransactionTemplate(ptm);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult((status) -> {
                System.out.println("Inner-1-1) Entities: " + repo.count());
                repo.save(new MyEntity());
                System.out.println("Inner-1-2) Entities: " + repo.count());
                // If we roll back, this will have no visible side-effects to the outer
                // transaction.
                status.setRollbackOnly();
            });

            System.out.println("Outer-1) Entities: " + repo.count());

            // If we execute code with a MNDATORY propagation, it will participate in the
            // existing outer transaction. This isn't really an "inner" transaction at all,
            // as I understand it.
            template.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
            template.executeWithoutResult((status) -> {
                System.out.println("Inner-2-1: Entities: " + repo.count());
                repo.save(new MyEntity());
                System.out.println("Inner-2-2: Entities: " + repo.count());

                // Not convinced this is the same transaction? Try rolling back! The "outer"
                // transaction will roll back as well, once the function returns.
                if (env.getProperty("do-rollback", Boolean.class, false)) {
                    status.setRollbackOnly();
                }
            });

            System.out.println("5. Visible entities: " + repo.count());
        }

        // No transactional annotation is required using the transaction template
        public void run2() {
            var template = new TransactionTemplate(ptm);
            template.executeWithoutResult(status -> {
                System.out.println("Committed entites: " + repo.count());
            });
        }

    }
}