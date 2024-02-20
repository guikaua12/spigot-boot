package me.approximations.apxPlugin.persistence.jpa.context;

import org.hibernate.Session;

public class PersistenceContext {
    private static final ThreadLocal<Session> THREAD_LOCAL_SESSION = new ThreadLocal<>();

    public static Session getSession() {
        return THREAD_LOCAL_SESSION.get();
    }

    public static void setSession(Session session) {
        THREAD_LOCAL_SESSION.set(session);
    }

    public static void removeSession() {
        THREAD_LOCAL_SESSION.remove();
    }

    public static boolean hasSession() {
        return THREAD_LOCAL_SESSION.get() != null;
    }
}
