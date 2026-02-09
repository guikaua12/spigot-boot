package tech.guilhermekaua.spigotboot.data.ormLite.utils;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.data.ormLite.repository.OrmLiteRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrmLiteTypeUtilsTest {

    // test entity classes
    static class People {
    }

    static class Order {
    }

    // direct interface extending OrmLiteRepository
    interface UserRepository extends OrmLiteRepository<People, UUID> {
    }

    // intermediate interface with partial binding
    interface BaseRepository<T> extends OrmLiteRepository<T, UUID> {
    }

    interface OrderRepository extends BaseRepository<Order> {
    }

    // multiple levels of indirection
    interface AbstractRepo<E, I> extends OrmLiteRepository<E, I> {
    }

    interface TypedRepo<E> extends AbstractRepo<E, UUID> {
    }

    interface DeepRepository extends TypedRepo<People> {
    }

    // unresolvable (raw type variables like OrmLiteRepositoryImpl<T, ID>)
    static class UnresolvableRepo<T, ID> implements OrmLiteRepository<T, ID> {
        public T save(T entity) {
            return null;
        }

        public java.util.List<T> saveAll(Iterable<T> iterable) {
            return null;
        }

        public T findById(ID id) {
            return null;
        }

        public java.util.List<T> findAll() {
            return null;
        }

        public void delete(T entity) {
        }

        public void delete(Iterable<T> iterable) {
        }

        public void deleteById(ID id) {
        }

        public void deleteAll() {
        }

        public long count() {
            return 0;
        }

        public boolean existsById(ID id) {
            return false;
        }

        public QueryBuilder<T, ID> queryBuilder() {
            return null;
        }

        public UpdateBuilder<T, ID> updateBuilder() {
            return null;
        }

        public DeleteBuilder<T, ID> deleteBuilder() {
            return null;
        }

        public Dao.CreateOrUpdateStatus createOrUpdate(T var1) {
            return null;
        }

        public int update(T var1) {
            return 0;
        }
    }

    // concrete class extending a generic impl with concrete types
    static class ConcreteUserRepo extends UnresolvableRepo<People, UUID> {
    }

    @Test
    void resolveEntityType_directInterface() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(UserRepository.class);
        assertEquals(People.class, entityType);
    }

    @Test
    void resolveEntityType_intermediateInterface() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(OrderRepository.class);
        assertEquals(Order.class, entityType);
    }

    @Test
    void resolveEntityType_deepHierarchy() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(DeepRepository.class);
        assertEquals(People.class, entityType);
    }

    @Test
    void resolveEntityType_unboundTypeVariables_returnsNull() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(UnresolvableRepo.class);
        assertNull(entityType, "Should return null for classes with unbound type variables");
    }

    @Test
    void resolveEntityType_concreteSubclassOfGenericImpl() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(ConcreteUserRepo.class);
        assertEquals(People.class, entityType);
    }

    @Test
    void resolveEntityType_unrelatedClass_returnsNull() {
        Class<?> entityType = OrmLiteTypeUtils.resolveEntityType(String.class);
        assertNull(entityType);
    }
}
