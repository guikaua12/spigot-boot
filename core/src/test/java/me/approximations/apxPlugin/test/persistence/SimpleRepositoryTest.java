//package me.approximations.apxPlugin.test.persistence;
//
//import me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig;
//import me.approximations.apxPlugin.persistence.jpa.config.PersistenceUnitConfig;
//import me.approximations.apxPlugin.persistence.jpa.config.impl.HikariPersistenceUnitConfig;
//import me.approximations.apxPlugin.persistence.jpa.proxy.handler.SharedSessionMethodHandler;
//import me.approximations.apxPlugin.persistence.jpa.repository.Repository;
//import me.approximations.apxPlugin.persistence.jpa.repository.impl.SimpleRepository;
//import org.hibernate.Session;
//import org.hibernate.dialect.Dialect;
//import org.hibernate.dialect.H2Dialect;
//import org.hibernate.jpa.HibernatePersistenceProvider;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import javax.persistence.EntityManagerFactory;
//import java.lang.reflect.InvocationTargetException;
//import java.sql.Driver;
//import java.time.Instant;
//import java.util.Arrays;
//import java.util.List;
//
//public class SimpleRepositoryTest {
//    public static final String H2_ADDRESS_FORMAT = "jdbc:h2:mem:%s";
//    static Repository<People, Long> peopleRepository;
//
//    @BeforeEach
//    public void beforeEach() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
//        final PersistenceConfig config = new PersistenceConfig() {
//            @Override
//            public String getPersistenceUnitName() {
//                return "test-persistence-unit";
//            }
//
//            @Override
//            public String getAddress() {
//                return String.format(H2_ADDRESS_FORMAT, "test");
//            }
//
//            @Override
//            public String getUsername() {
//                return "sa";
//            }
//
//            @Override
//            public String getPassword() {
//                return "";
//            }
//
//            @Override
//            public Class<? extends Driver> getJdbcDriver() {
//                return org.h2.Driver.class;
//            }
//
//            @Override
//            public boolean showSql() {
//                return true;
//            }
//
//            @Override
//            public Class<? extends Dialect> getDialect() {
//                return H2Dialect.class;
//            }
//
//            @Override
//            public String getDdlAuto() {
//                return "none";
//            }
//        };
//
//        final PersistenceUnitConfig persistenceUnitConfig = new HikariPersistenceUnitConfig(config, Arrays.asList(People.class));
//        final HibernatePersistenceProvider provider = new HibernatePersistenceProvider();
//
//        final EntityManagerFactory entityManagerFactory = provider
//                .createContainerEntityManagerFactory(
//                        persistenceUnitConfig,
//                        persistenceUnitConfig.getProperties()
//                );
//
//        final Session sharedEntityManagerProxy = SharedSessionMethodHandler.createProxy(entityManagerFactory);
//
//        peopleRepository = new SimpleRepository<>(sharedEntityManagerProxy, People.class);
//        peopleRepository.deleteAll();
//    }
//
//    @Test
//    public void shouldInsert() {
//        final People people = new People(null, "test", "test@gmail.com", Instant.now());
//        Assertions.assertNull(people.getId());
//
//        peopleRepository.save(people);
//
//        Assertions.assertNotNull(people.getId());
//    }
//
//    @Test
//    public void shouldFindById() {
//        final People people = new People(null, "test", "test@test.com", Instant.now());
//        peopleRepository.save(people);
//
//        final People foundPeople = peopleRepository.findById(people.getId());
//        Assertions.assertNotNull(foundPeople);
//        Assertions.assertEquals(people.getId(), foundPeople.getId());
//    }
//
//    @Test
//    public void shouldFindAll() {
//        final People people1 = new People(null, "test1", "test1@test.com", Instant.now());
//        final People people2 = new People(null, "test2", "test2@test.com", Instant.now());
//        final People people3 = new People(null, "test3", "test3@test.com", Instant.now());
//        peopleRepository.save(people1);
//        peopleRepository.save(people2);
//        peopleRepository.save(people3);
//
//        final List<People> people = peopleRepository.findAll();
//        Assertions.assertNotNull(people);
//        Assertions.assertEquals(3, people.size());
//        Assertions.assertEquals(people, Arrays.asList(people1, people2, people3));
//    }
//
//    @Test
//    public void shouldDelete() {
//        final People people = new People(null, "test", "test@test.com", Instant.now());
//        peopleRepository.save(people);
//
//        Assertions.assertEquals(1, peopleRepository.count());
//        peopleRepository.delete(people);
//        Assertions.assertEquals(0, peopleRepository.count());
//    }
//
//    @Test
//    public void shouldDeleteById() {
//        final People people = new People(null, "test", "test@test.com", Instant.now());
//        peopleRepository.save(people);
//
//        Assertions.assertEquals(1, peopleRepository.count());
//        peopleRepository.deleteById(people.getId());
//        Assertions.assertEquals(0, peopleRepository.count());
//    }
//
//    @Test
//    public void shouldDeleteAll() {
//        final People people1 = new People(null, "test1", "test@test.com", Instant.now());
//        final People people2 = new People(null, "test2", "test@test.com", Instant.now());
//        final People people3 = new People(null, "test3", "test@test.com", Instant.now());
//        peopleRepository.save(people1);
//        peopleRepository.save(people2);
//        peopleRepository.save(people3);
//
//        Assertions.assertEquals(3, peopleRepository.count());
//        peopleRepository.deleteAll();
//        Assertions.assertEquals(0, peopleRepository.count());
//    }
//
//    @Test
//    public void shouldCount() {
//        final People people1 = new People(null, "test1", "test@test.com", Instant.now());
//        final People people2 = new People(null, "test2", "test@test.com", Instant.now());
//        final People people3 = new People(null, "test3", "test@test.com", Instant.now());
//        peopleRepository.save(people1);
//        peopleRepository.save(people2);
//        peopleRepository.save(people3);
//
//        Assertions.assertEquals(3, peopleRepository.count());
//    }
//
//    @Test
//    public void shouldExistsById() {
//        final People people = new People(null, "test", "test@test.com", Instant.now());
//        peopleRepository.save(people);
//
//        Assertions.assertTrue(peopleRepository.existsById(people.getId()));
//
//        peopleRepository.delete(people);
//
//        Assertions.assertFalse(peopleRepository.existsById(people.getId()));
//    }
//}
