package utils;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;
import tukano.api.Result;
import tukano.api.Result.ErrorCode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using
 * Hibernate and a backing relational database.
 *
 * @param <Session>
 */
public class Hibernate {
	private static SessionFactory sessionFactory;
	private static Hibernate instance;

	private static final ThreadLocal<Session> threadLocalSession = new ThreadLocal<>();

	private Hibernate() {
		try {
			sessionFactory = new Configuration().configure().buildSessionFactory();
		} catch (Exception e) {
			System.err.println("Error initializing Hibernate SessionFactory: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static synchronized Hibernate getInstance() {
		if (instance == null) {
			instance = new Hibernate();
		}
		return instance;
	}

	private Session getCurrentSession() {
		Session session = threadLocalSession.get();
		if (session == null || !session.isOpen()) {
			session = sessionFactory.openSession();
			threadLocalSession.set(session);
		}
		return session;
	}

	private void closeCurrentSession() {
		Session session = threadLocalSession.get();
		if (session != null && session.isOpen()) {
			session.close();
		}
		threadLocalSession.remove();
	}

	public Result<Void> persistOne(Object obj) {
		return execute((hibernate) -> {
			hibernate.persist(obj);
		});
	}

	public <T> Result<T> updateOne(T obj) {
		return execute(hibernate -> {
			var res = hibernate.merge(obj);
			if (res == null)
				return Result.error(ErrorCode.NOT_FOUND);

			return Result.ok(res);
		});
	}

	public <T> Result<T> deleteOne(T obj) {
		return execute(hibernate -> {
			hibernate.remove(obj);
			return Result.ok(obj);
		});
	}

	public <T> Result<T> getOne(Object id, Class<T> clazz) {
		return execute(session -> {
			var res = session.find(clazz, id);
			if (res == null)
				return Result.error(ErrorCode.NOT_FOUND);
			return Result.ok(res);
		});
	}

	public <T> Result<List<T>> sql(String sqlStatement, Class<T> clazz) {
		return execute(session -> {
			var query = session.createNativeQuery(sqlStatement, clazz);
			return Result.ok(query.list());
		});
	}

	public <T> Result<T> execute(Consumer<Session> proc) {
		return execute((hibernate) -> {
			proc.accept(hibernate);
			return Result.ok();
		});
	}

	public <T> Result<T> execute(Function<Session, Result<T>> func) {
		Transaction tx = null;
		try {
			Session session = getCurrentSession();
			tx = session.beginTransaction();
			var res = func.apply(session);
			session.flush();
			tx.commit();
			return res;
		} catch (ConstraintViolationException __) {
			if (tx != null) tx.rollback();
			return Result.error(ErrorCode.CONFLICT);
		} catch (Exception e) {
			if (tx != null) tx.rollback();
			e.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);
		} finally {
			closeCurrentSession();
		}
	}

	public void shutdown() {
		if (sessionFactory != null) {
			sessionFactory.close();
		}
	}
}
