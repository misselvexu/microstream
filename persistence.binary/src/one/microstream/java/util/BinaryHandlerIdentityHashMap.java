package one.microstream.java.util;

import java.util.IdentityHashMap;

import one.microstream.X;
import one.microstream.chars.XChars;
import one.microstream.persistence.binary.internal.AbstractBinaryHandlerCustomCollection;
import one.microstream.persistence.binary.types.Binary;
import one.microstream.persistence.exceptions.PersistenceException;
import one.microstream.persistence.types.Persistence;
import one.microstream.persistence.types.PersistenceFunction;
import one.microstream.persistence.types.PersistenceLoadHandler;
import one.microstream.persistence.types.PersistenceReferenceLoader;
import one.microstream.persistence.types.PersistenceStoreHandler;


public final class BinaryHandlerIdentityHashMap extends AbstractBinaryHandlerCustomCollection<IdentityHashMap<?, ?>>
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////

	static final long BINARY_OFFSET_ELEMENTS = 0;
	// to prevent recurring confusion: IdentityHashMap really has no loadFactor. It uses an open adressing hash array.



	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Class<IdentityHashMap<?, ?>> handledType()
	{
		return (Class)IdentityHashMap.class; // no idea how to get ".class" to work otherwise
	}

	static final int getElementCount(final Binary data)
	{
		return X.checkArrayRange(data.getListElementCountKeyValue(BINARY_OFFSET_ELEMENTS));
	}
	
	public static BinaryHandlerIdentityHashMap New()
	{
		return new BinaryHandlerIdentityHashMap();
	}



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BinaryHandlerIdentityHashMap()
	{
		super(
			handledType(),
			keyValuesFields()
		);
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public final void store(
		final Binary                  data    ,
		final IdentityHashMap<?, ?>   instance,
		final long                    objectId,
		final PersistenceStoreHandler handler
	)
	{
		// store elements simply as array binary form
		data.storeMapEntrySet(
			this.typeId()         ,
			objectId              ,
			BINARY_OFFSET_ELEMENTS,
			instance.entrySet()   ,
			handler
		);
	}
	
	@Override
	public final IdentityHashMap<?, ?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new IdentityHashMap<>(
			getElementCount(data)
		);
	}

	@Override
	public final void updateState(
		final Binary                 data    ,
		final IdentityHashMap<?, ?>  instance,
		final PersistenceLoadHandler handler
	)
	{
		instance.clear();
		
		@SuppressWarnings("unchecked")
		final IdentityHashMap<Object, Object> castedInstance = (IdentityHashMap<Object, Object>)instance;
		
		// IdentityHashMap does not need the elementsHelper detour as identity hashing does not depend on contained data
		data.collectKeyValueReferences(
			BINARY_OFFSET_ELEMENTS,
			getElementCount(data),
			handler,
			(k, v) ->
			{
				if(castedInstance.putIfAbsent(k, v) != null)
				{
					// (22.04.2016 TM)EXCP: proper exception
					throw new PersistenceException(
						"Duplicate key reference in " + IdentityHashMap.class.getSimpleName()
						+ " " + XChars.systemString(instance)
					);
				}
			}
		);
	}
	
	@Override
	public final void iterateInstanceReferences(final IdentityHashMap<?, ?> instance, final PersistenceFunction iterator)
	{
		Persistence.iterateReferencesMap(iterator, instance);
	}

	@Override
	public final void iterateLoadableReferences(final Binary data, final PersistenceReferenceLoader iterator)
	{
		data.iterateKeyValueEntriesReferences(BINARY_OFFSET_ELEMENTS, iterator);
	}
	
}
