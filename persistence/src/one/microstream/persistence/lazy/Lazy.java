package one.microstream.persistence.lazy;

import java.util.function.Predicate;

import one.microstream.chars.XChars;
import one.microstream.memory.MemoryStatistics;
import one.microstream.memory.MemoryStatisticsProvider;
import one.microstream.persistence.types.Persistence;
import one.microstream.persistence.types.PersistenceObjectRetriever;
import one.microstream.reference.LazyReferencing;


/**
 * A reference providing generic lazy-loading functionality.
 * <p>
 * Note that the shortened name has been chosen intentionally to optimize readability in class design.
 * <p>
 * Also note that a type like this is strongly required to implement lazy loading behavior in an architectural
 * clean and proper way. I.e. the design has to define that a certain reference is meant be capable of lazy-loading.
 * If such a definition is not done, a loading logic is strictly required to always load the encountered reference,
 * as it is defined by the normal reference.
 * Any "tricks" of whatever framework to "sneak in" lazy loading behavior where it hasn't actually been defined
 * are nothing more than dirty hacks and mess up if not destroy the program's consistency of state
 * (e.g. antipatterns like secretly replacing a well-defined collection instance with a framework-proprietary
 * proxy instance of a "similar" collection implementation).
 * In proper architectured sofware, if a reference does not define lazy loading capacity, it is not wanted to have
 * that capacity on the business logical level by design in the first place. Any attempts of saying
 * "but I want it anyway in a sneaky 'transparent' way" indicate ambivalent conflicting design errors and thus
 * in the end poor design.
 *
 * @author Thomas Muenz
 * @param <T>
 */
public interface Lazy<T> extends LazyReferencing<T>
{
	public T clear();

	public boolean isStored();

	public boolean isLoaded();
	
	

	public static <T> T get(final Lazy<T> reference)
	{
		if(reference == null)
		{
			return null; // debug-hook
		}
		
		return reference.get();
	}

	public static <T> T peek(final Lazy<T> reference)
	{
		if(reference == null)
		{
			return null; // debug-hook
		}
		
		return reference.peek();
	}

	public static void clear(final Lazy<?> reference)
	{
		if(reference == null)
		{
			return; // debug-hook
		}
		
		reference.clear();
	}
	
	public static boolean isStored(final Lazy<?> reference)
	{
		if(reference == null)
		{
			return false; // debug-hook
		}
		
		return reference.isStored();
	}
	
	public static boolean isLoaded(final Lazy<?> reference)
	{
		if(reference == null)
		{
			// philosophical question: is a non-existent reference implicitely always loaded or never loaded?
			return false; // debug-hook
		}
		
		return reference.isLoaded();
	}

	public static <T> Lazy<T> Reference(final T subject)
	{
		return register(new Lazy.Default<>(subject));
	}

	public static <T> Lazy<T> New(final long objectId)
	{
		return register(new Lazy.Default<>(null, objectId, null));
	}

	public static <T> Lazy<T> New(final long objectId, final PersistenceObjectRetriever loader)
	{
		return register(new Lazy.Default<>(null, objectId, loader));
	}

	public static <T> Lazy<T> New(final T subject, final long objectId, final PersistenceObjectRetriever loader)
	{
		return register(new Lazy.Default<>(subject, objectId, loader));
	}

	public static LazyReferenceManager.Checker Checker(final long millisecondTimeout)
	{
		return new Default.Checker(millisecondTimeout, null);
	}

	public static LazyReferenceManager.Checker Checker(
		final long                        millisecondTimeout       ,
		final Predicate<MemoryStatistics> memoryBoundClearPredicate
	)
	{
		return new Default.Checker(millisecondTimeout, memoryBoundClearPredicate);
	}

	public static LazyReferenceManager.Checker Checker(final Predicate<MemoryStatistics> memoryBoundClearPredicate)
	{
		return new Default.Checker(-1, memoryBoundClearPredicate);
	}

	public static <T, L extends Lazy<T>> L register(final L lazyReference)
	{
		LazyReferenceManager.get().register(lazyReference);
		return lazyReference;
	}
	
	public final class Default<T> implements Lazy<T>
	{
		@SuppressWarnings("all")
		public static final Class<Lazy.Default<?>> genericType()
		{
			// no idea how to get ".class" to work otherwise in conjunction with generics.
			return (Class)Lazy.Default.class;
		}



		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		/**
		 * The actual subject to be referenced.
		 */
		private T subject;
		
		/**
		 * The timestamp in milliseconds when this reference has last been touched (created or queried).
		 * If an instance is deemed timed out by a {@link LazyReferenceManager} based on the current time
		 * and some arbitrary timeout threshold, its subject gets cleared.
		 */
		transient long lastTouched;

		/**
		 * The cached object id of the not loaded actual instance to later load it lazily.
		 * Although this value never changes logically during the lifetime of an instance,
		 * it might be delayed initialized. See {@link #link(long, PersistenceObjectRetriever)} and its use site(s).
		 */
		// CHECKSTYLE.OFF: VisibilityModifier CheckStyle false positive for same package in another project
		transient long objectId;
		// CHECKSTYLE.ON: VisibilityModifier

		/**
		 * The loader to be used for loading the actual subject via the deposited object id.
		 * This should typically be the loader instance that should have loaded the actual instance
		 * in the first place but did not to do its work later lazyely. Apart from this idea,
		 * there is no "hard" contract on what the loader instance should specifically be.
		 */
		private transient PersistenceObjectRetriever loader;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		/**
		 * Standard constructor used by normal logic to instantiate a reference.
		 *
		 * @param subject the subject to be referenced.
		 */
		Default(final T subject)
		{
			this(subject, Persistence.nullId(), null);
		}

		/**
		 * Special constructor used by logic that lazily skips the actual instance (e.g. internal loading logic)
		 * but instead provides means to get the instance at a later point in time.
		 *
		 * @param subject the potentially already present subject to be referenced or null.
		 * @param objectId the subject's object id under which it can be reconstructed by the provided loader
		 * @param loader the loader used to reconstruct the actual instance originally referenced
		 */
		Default(final T subject, final long objectId, final PersistenceObjectRetriever loader)
		{
			super();
			this.subject  = subject ;
			this.objectId = objectId;
			this.loader   = loader  ;
			this.touch();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		public final long objectId()
		{
			return this.objectId;
		}
		
		public final long lastTouched()
		{
			return this.lastTouched;
		}
		
		@Override
		public final synchronized boolean isStored()
		{
			// checking objectId rather than loader as loader might be initially non-null in a future enhancement
			return this.objectId != Persistence.nullId();
		}
		
		@Override
		public final synchronized boolean isLoaded()
		{
			// the funny thing here is: even if the lazy reference has been initialized with null, the result ist correct.
			return this.subject != null;
		}

		/**
		 * Returns the wrapped reference without loading it on demand.
		 *
		 * @return the current reference withouth on-demand loading.
		 */
		@Override
		public final synchronized T peek()
		{
			return this.subject;
		}

		/**
		 * Clears the reference, leaving the option to re-load it again intact, and returns the subject that was
		 * referenced prior to clearing.
		 *
		 * @return the subject referenced prior to clearing the reference.
		 */
		@Override
		public final synchronized T clear()
		{
			final T subject = this.subject;
			this.internalClear();
			return subject;
		}

		private void touch()
		{
			this.lastTouched = this.subject != null
				? System.currentTimeMillis()
				: Long.MAX_VALUE
			;
		}

		private void validateObjectIdToBeSet(final long objectId)
		{
			if(this.objectId != Persistence.nullId() && this.objectId != objectId)
			{
				// (22.10.2014)TODO: proper exception
				throw new RuntimeException("ObjectId already set: " + this.objectId);
			}
		}

		final synchronized void setLoader(final PersistenceObjectRetriever loader)
		{
			/*
			 * This method might be called when storing or building to/from different sources
			 * (e.g. client-server communication instead of database storing).
			 * To keep the logic simple, only the first non-null passing call will be considered.
			 * It is assumed that the application logic is tailored in such a way that the "primary" or "actual"
			 * loader (probably for a database) will be passed here first.
			 * If this is not sufficient for certain more complex demands, this class (and its binary handler etc.)
			 * can always be replaced by a copy derived from it. Nothing magical about it (nothing hardcoded
			 * somewhere in the persisting logic or such).
			 */
			if(this.loader != null)
			{
				/* (03.09.2019 TM)FIXME: Lazy Reference loader link
				 * The current naive approach means holding on to a certain connection's persistence manager forever,
				 * which is a bad thing.
				 * However, the logic below of throwing an exception is even worse since it would crash the thread
				 * on every newly created connection used to load a lazy reference.
				 * The proper solution would be to link a more general, central, long-living instance, like the
				 * EmbeddedStorageManager ("the database" instance) itself.
				 * However: maybe EmbeddedStorageManager should be referenced only weakly.
				 */
				return;
				
				// (03.09.2019 TM)NOTE: not possible since every connection instance gets a new persistence manager instance
//				if(this.loader == loader)
//				{
//					return;
//				}
	//
//				// (03.09.2019 TM)EXCP: proper exception
//				throw new RuntimeException(
//					"Lazy reference is already linked to another "
//					+ PersistenceObjectRetriever.class.getSimpleName()
//				);
			}
			
			this.loader = loader;
		}

		final synchronized void link(final long objectId, final PersistenceObjectRetriever loader)
		{
			this.validateObjectIdToBeSet(objectId);
			this.setLoader(loader);
			this.objectId = objectId;
		}

		private void internalClear()
		{
			// (03.09.2019 TM)NOTE: may never clear an unstored reference
			if(!this.isStored())
			{
				// (03.09.2019 TM)EXCP: proper exception
				throw new RuntimeException("Cannot clear an unstored lazy reference.");
			}
			
			this.internalClearUnchecked();
		}

		private void internalClearUnchecked()
		{
//			XDebug.println("Clearing " + Lazy.class.getSimpleName() + " " + this.subject);
			this.subject = null;
			this.touch();
		}

		final synchronized void clearConditional(final long millisecondThreshold, final boolean clearMemoryBound)
		{
//			XDebug.println("Checking " + this.subject + ": " + this.lastTouched + " vs " + millisecondThreshold
//				+ ", clearMemoryBound = " + clearMemoryBound);

			/* Memory bound and time check implicitely covers already cleared reference.
			 * May of course not clear unstored references.
			 */
			if((!clearMemoryBound && this.lastTouched >= millisecondThreshold) || !this.isStored())
			{
				return;
			}

//			XDebug.println("clearing " + this.objectId + ": " + XChars.systemString(this.subject));
			this.internalClearUnchecked();
		}


		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		/**
		 * Returns the original subject referenced by this reference instance.
		 * If the subject has (lazily) not been loaded, an attempt to do so now is made.
		 * Any exception occuring during the loading attempt will be passed along without currupting this
		 * reference instance's internal state.
		 *
		 * @return the originally referenced subject, either already-known or lazy-loaded.
		 */
		@Override
		public final synchronized T get()
		{
			if(this.subject == null && this.objectId != Persistence.nullId())
			{
				this.load();
			}
			
			/* There are 3 possible cases at this point:
			 * 1.) subject is not null because it has been set via the normal constructor directly and is simply returned
			 * 2.) subject was lazily null but has been successfully thread-safely loaded, set and can now be returned
			 * 3.) subject was null in the first place (one way or another) and null gets returned.
			 */
			this.touch();
			
			return this.subject;
		}

		@SuppressWarnings("unchecked") // safety of cast guaranteed by logic
		private synchronized void load()
		{
			// this context doesn't have to do anything on an exception inside the get(), just pass it along
			this.subject = (T)this.loader.getObject(this.objectId);
		}

		@Override
		public String toString()
		{
			return this.subject == null
				? "(" + this.objectId + " not loaded)"
				: this.objectId + " " + XChars.systemString(this.subject)
			;
		}



		public static final class Checker implements LazyReferenceManager.Checker
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final long                        millisecondTimeout       ;
			private       long                        millisecondThreshold     ;

			private final Predicate<MemoryStatistics> memoryBoundClearPredicate;
			private       boolean                     clearMemoryBound         ;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			Checker(
				final long                        millisecondTimeout       ,
				final Predicate<MemoryStatistics> memoryBoundClearPredicate
			)
			{
				super();
				this.millisecondTimeout        = millisecondTimeout       ;
				this.memoryBoundClearPredicate = memoryBoundClearPredicate;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public void beginCheckCycle()
			{
				this.millisecondThreshold = this.millisecondTimeout > 0
					? System.currentTimeMillis() - this.millisecondTimeout
					: -1;
				
				this.clearMemoryBound = this.memoryBoundClearPredicate != null
					? this.memoryBoundClearPredicate.test(MemoryStatisticsProvider.get().heapMemoryUsage())
					: false;
					
//				XDebug.println("Begin check cycle: millisecondThreshold = " + this.millisecondThreshold
//					+ ", clearMemoryBound = " + this.clearMemoryBound
//					+ ", quota = " + MemoryStatisticsProvider.get().heapMemoryUsage().quota());
			}

			@Override
			public void accept(final LazyReferencing<?> lazyReference)
			{
				if(!(lazyReference instanceof Lazy.Default<?>))
				{
					return;
				}
				((Lazy.Default<?>)lazyReference).clearConditional(
					this.millisecondThreshold,
					this.clearMemoryBound
				);
			}

		}

	}

}
