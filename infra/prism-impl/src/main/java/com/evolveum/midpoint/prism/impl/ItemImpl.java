/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.prism.impl;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.equivalence.EquivalenceStrategy;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.equivalence.ParameterizedEquivalenceStrategy;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * Item is a common abstraction of Property and PropertyContainer.
 * <p>
 * This is supposed to be a superclass for all items. Items are things
 * that can appear in property containers, which generally means only a property
 * and property container itself. Therefore this is in fact superclass for those
 * two definitions.
 *
 * @author Radovan Semancik
 */
public abstract class ItemImpl<V extends PrismValue, D extends ItemDefinition> implements Item<V, D> {

    private static final long serialVersionUID = 510000191615288733L;

    // The object should basically work without definition and prismContext. This is the
	// usual case when it is constructed "out of the blue", e.g. as a new JAXB object
	// It may not work perfectly, but basic things should work
    protected ItemName elementName;
    protected PrismContainerValue<?> parent;
    protected D definition;
    @NotNull protected final List<V> values = new ArrayList<>();
    private transient Map<String,Object> userData = new HashMap<>();;

    protected EquivalenceStrategy defaultEquivalenceStrategy;

	protected boolean immutable;
	protected boolean incomplete;

    protected transient PrismContext prismContext;          // beware, this one can easily be null

    /**
     * This is used for definition-less construction, e.g. in JAXB beans.
     *
     * The constructors should be used only occasionally (if used at all).
     * Use the factory methods in the ResourceObjectDefintion instead.
     */
    ItemImpl(QName elementName) {
        super();
        this.elementName = ItemName.fromQName(elementName);
    }

    ItemImpl(QName elementName, PrismContext prismContext) {
        super();
        this.elementName = ItemName.fromQName(elementName);
        this.prismContext = prismContext;
    }


    /**
     * The constructors should be used only occasionally (if used at all).
     * Use the factory methods in the ResourceObjectDefinition instead.
     */
    ItemImpl(QName elementName, D definition, PrismContext prismContext) {
        super();
        this.elementName = ItemName.fromQName(elementName);
        this.definition = definition;
        this.prismContext = prismContext;
    }

	static <T extends Item> T createNewDefinitionlessItem(QName name, Class<T> type, PrismContext prismContext) {
		T item;
			try {
				//noinspection unchecked
				Constructor<T> constructor = toImplClass(type).getConstructor(QName.class);
				item = constructor.newInstance(name);
	        if (prismContext != null) {
	            item.revive(prismContext);
	        }
			} catch (Exception e) {
				throw new SystemException("Error creating new definitionless "+type.getSimpleName()+": "+e.getClass().getName()+" "+e.getMessage(),e);
			}
		return item;
	}

	private static <T> Class toImplClass(Class<T> type) {
		if (PrismProperty.class.equals(type)) {
			return PrismPropertyImpl.class;
		} else if (PrismReference.class.equals(type)) {
			return PrismReferenceImpl.class;
		} else if (PrismContainer.class.equals(type)) {
			return PrismContainerImpl.class;
		} else {
			return type;    // or throw an exception?
		}
	}

    @Override
    public D getDefinition() {
        return definition;
    }

	@Override
	public boolean hasCompleteDefinition() {
		return getDefinition() != null;
	}

    @Override
    public ItemName getElementName() {
        return elementName;
    }

    @Override
    public void setElementName(QName elementName) {
		checkMutability();
        this.elementName = ItemName.fromQName(elementName);
    }

    /**
     * Sets applicable property definition.
     *
     * @param definition the definition to set
     */
    @Override
    public void setDefinition(D definition) {
		checkMutability();
    	checkDefinition(definition);
        this.definition = definition;
    }

	@Override
    public String getDisplayName() {
        return getDefinition() == null ? null : getDefinition().getDisplayName();
    }

    @Override
    public String getHelp() {
        return getDefinition() == null ? null : getDefinition().getHelp();
    }

    public boolean isIncomplete() {
		return incomplete;
	}

	public void setIncomplete(boolean incomplete) {
		this.incomplete = incomplete;
	}

	@Override
    public PrismContext getPrismContext() {
		if (prismContext != null) {
			return prismContext;
		} else if (parent != null) {
			return parent.getPrismContext();
		} else {
			return null;
		}
    }

    // Primarily for testing
    public PrismContext getPrismContextLocal() {
		return prismContext;
	}

    public void setPrismContext(PrismContext prismContext) {
		this.prismContext = prismContext;
	}

	public PrismContainerValue<?> getParent() {
    	return parent;
    }

    public void setParent(PrismContainerValue<?> parentValue) {
    	if (this.parent != null && parentValue != null && this.parent != parentValue) {
    		throw new IllegalStateException("Attempt to reset parent of item "+this+" from "+this.parent+" to "+parentValue);
    	}
    	// Immutability check can be skipped, as setting the parent doesn't alter this object.
		// However, if existing parent itself is immutable, adding/removing its child item will cause the exception.
    	this.parent = parentValue;
    }

	protected Object getPathComponent() {
		ItemName elementName = getElementName();
		if (elementName != null) {
			return elementName;
		} else {
			throw new IllegalStateException("Unnamed item has no path");
		}
	}

	@Nullable
	@Override
	public Object getRealValue() {
		V value = getValue();
		return value != null ? value.getRealValue() : null;
	}

	@NotNull
    public ItemPath getPath() {
    	 if (parent == null) {
		     if (getElementName() != null) {
			     return getElementName();
		     } else {
		     	throw new IllegalStateException("Unnamed item has no path");
		     }
    	 }
    	 /*
    	  * This quite ugly algorithm is here to eliminate the need to repeatedly call itemPath.append(..) method
    	  * that leads to creation of many little objects on the heap. Instead we simply collect path segments
    	  * and merge them to a single item path in one operation.
    	  *
    	  * TODO This is not very nice solution. Think again about it.
    	  */
    	 List<Object> names = new ArrayList<>();
    	 acceptParentVisitor(v -> {
    	 	Object pathComponent;
    	 	if (v instanceof Item) {
		        if (v instanceof ItemImpl) {
			        pathComponent = ((ItemImpl) v).getPathComponent();
		        } else {
			        throw new IllegalStateException("Expected ItemImpl but got " + v.getClass());
		        }
	        } else if (v instanceof PrismValue) {
    	 		if (v instanceof PrismValueImpl) {
    	 			pathComponent = ((PrismValueImpl) v).getPathComponent();
		        } else {
			        throw new IllegalStateException("Expected PrismValueImpl but got " + v.getClass());
		        }
	        } else if (v instanceof Itemable) {     // e.g. a delta
    	 		pathComponent = ((Itemable) v).getPath();
	        } else {
		        throw new IllegalStateException("Expected Item or PrismValue but got " + v.getClass());
	        }
    	 	if (pathComponent != null) {
    	 		names.add(pathComponent);
	        }
	     });
    	 return ItemPath.createReverse(names);
    }

	@Override
	public void acceptParentVisitor(@NotNull Visitor visitor) {
		visitor.visit(this);
		if (parent != null) {
			parent.acceptParentVisitor(visitor);
		}
	}

	@NotNull
	public Map<String, Object> getUserData() {
		if (userData == null) {
			userData = new HashMap<>();
		}
		if (immutable) {
			return Collections.unmodifiableMap(userData);			// TODO beware, objects in userData themselves are mutable
		} else {
			return userData;
		}
	}

    public <T> T getUserData(String key) {
		// TODO make returned data immutable (?)
		return (T) getUserData().get(key);
	}

    public void setUserData(String key, Object value) {
		checkMutability();
    	getUserData().put(key, value);
    }

    @NotNull
	public List<V> getValues() {
		return values;
	}

	@Override
	public V getAnyValue() {
		return !values.isEmpty() ? values.get(0) : null;
	}

	@Override
	public V getValue() {
		if (values.isEmpty()) {
			return null;
		} else if (values.size() == 1) {
			return values.get(0);
		} else {
			throw new IllegalStateException("Attempt to get single value from item " + getElementName() + " with multiple values");
		}
	}

	public boolean isSingleValue() {
    	// TODO what about dynamic definitions? See MID-3922
		if (getDefinition() != null) {
    		if (getDefinition().isMultiValue()) {
    			return false;
    		}
    	}
		return values.size() <= 1;
	}

	@Override
    public V findValue(V value, @NotNull EquivalenceStrategy strategy) {
        for (V myVal : getValues()) {
            if (myVal.equals(value, strategy)) {
                return myVal;
            }
        }
        return null;
    }

//    public List<? extends PrismValue> findValuesIgnoringMetadata(PrismValue value) {
//    	return getValues().stream()
//			    .filter(v -> v.equalsComplex(value, true, false))
//			    .collect(Collectors.toList());
//    }

	public Collection<V> getClonedValues() {
    	Collection<V> clonedValues = new ArrayList<>(getValues().size());
    	for (V val: getValues()) {
    		clonedValues.add((V)val.clone());
    	}
		return clonedValues;
	}

    public boolean contains(V value) {
    	return contains(value, getEqualsHashCodeStrategy());
    }

    public boolean containsEquivalentValue(V value) {
    	return containsEquivalentValue(value, null);
    }

    public boolean containsEquivalentValue(V value, @Nullable Comparator<V> comparator) {
    	return contains(value, EquivalenceStrategy.IGNORE_METADATA_CONSIDER_DIFFERENT_IDS, comparator);
    }

    public boolean contains(V value, @NotNull EquivalenceStrategy strategy) {
	    for (V myValue: getValues()) {
		    if (strategy.equals(myValue, value)) {
			    return true;
		    }
	    }
    	return false;
    }

    @Override
    public boolean contains(V value, EquivalenceStrategy strategy, Comparator<V> comparator) {
    	if (comparator == null) {
    		if (strategy == null) {
			    return contains(value);
		    } else {
    			return contains(value, strategy);
		    }
	    } else {
		    for (V myValue : getValues()) {
			    if (comparator.compare(myValue, value) == 0) {
				    return true;
			    }
		    }
		    return false;
	    }
    }

    public boolean valuesEqual(Collection<V> matchValues, Comparator<V> comparator) {
    	return MiscUtil.unorderedCollectionCompare(values, matchValues, comparator);
    }

    public int size() {
    	return values.size();
    }

    public boolean addAll(Collection<V> newValues) throws SchemaException {
		checkMutability();
    	boolean changed = false;
    	for (V val: newValues) {
    		if (add(val)) {
    			changed = true;
    		}
    	}
    	return changed;
    }

    public boolean addAll(Collection<V> newValues, EquivalenceStrategy strategy) throws SchemaException {
		checkMutability();
    	boolean changed = false;
    	for (V val: newValues) {
    		if (add(val, strategy)) {
    			changed = true;
    		}
    	}
    	return changed;
    }

    public boolean add(@NotNull V newValue) throws SchemaException {
    	return add(newValue, true, getEqualsHashCodeStrategy());
    }

	@Override
	public boolean add(@NotNull V newValue, @NotNull EquivalenceStrategy equivalenceStrategy) throws SchemaException {
		return add(newValue, true, equivalenceStrategy);
	}

	public boolean add(@NotNull V newValue, boolean checkUniqueness) throws SchemaException {
    	return add(newValue, checkUniqueness, getEqualsHashCodeStrategy());
	}

	// equivalenceStrategy must not be null if checkUniqueness is true
	public boolean add(@NotNull V newValue, boolean checkUniqueness, EquivalenceStrategy equivalenceStrategy) throws SchemaException {
		checkMutability();
		if (newValue.getPrismContext() == null) {
			newValue.setPrismContext(prismContext);
		}
		Itemable originalParent = newValue.getParent();
		newValue.setParent(this);       // needed e.g. because of PrismReferenceValue comparisons
    	if (checkUniqueness && contains(newValue, equivalenceStrategy)) {
    		newValue.setParent(originalParent);
    		return false;
    	}
	    D definition = getDefinition();
	    if (definition != null) {
		    if (!values.isEmpty() && definition.isSingleValue()) {
			    throw new SchemaException("Attempt to put more than one value to single-valued item " + this + "; newly added value: " + newValue);
		    }
    		newValue.applyDefinition(definition, false);
    	}
    	return values.add(newValue);
    }

	public boolean removeAll(Collection<V> newValues) {
		checkMutability();
    	boolean changed = false;
    	for (V val: newValues) {
    		if (remove(val)) {
    			changed = true;
    		}
    	}
    	return changed;
    }

    public boolean remove(V newValue) {
	    checkMutability();
    	boolean changed = false;
    	Iterator<V> iterator = values.iterator();
    	while (iterator.hasNext()) {
    		V val = iterator.next();
			// the same algorithm as when deleting the item value from delete delta
			if (val.representsSameValue(newValue, false) || val.equals(newValue, EquivalenceStrategy.REAL_VALUE_CONSIDER_DIFFERENT_IDS)) {
    			iterator.remove();
    			val.setParent(null);
    			changed = true;
    		}
    	}
    	return changed;
    }

	@Override
	public boolean remove(V value, @NotNull EquivalenceStrategy strategy) {
		checkMutability();
		boolean changed = false;
		Iterator<V> iterator = values.iterator();
		while (iterator.hasNext()) {
			V val = iterator.next();
			if (val.representsSameValue(value, false) || val.equals(value, strategy)) {
				iterator.remove();
				val.setParent(null);
				changed = true;
			}
		}
		return changed;
	}

	public V remove(int index) {
		checkMutability();
    	V removed = values.remove(index);
    	removed.setParent(null);
    	return removed;
    }

    @Override
    public void replaceAll(Collection<V> newValues, EquivalenceStrategy strategy) throws SchemaException {
		checkMutability();
		clear();
    	addAll(newValues, strategy);
    }

	@Override
    public void replace(V newValue) throws SchemaException {
		checkMutability();
	    clear();
	    add(newValue);
    }

	@Override
    public void clear() {
		checkMutability();
	    for (V value : values) {
		    value.setParent(null);
	    }
		values.clear();
    }

	@Override
    public void normalize() {
		checkMutability();
		for (V value : values) {
			value.normalize();
		}
    }

    /**
     * Merge all the values of other item to this item.
     */
    @Override
    public void merge(Item<V,D> otherItem) throws SchemaException {
    	for (V otherValue: otherItem.getValues()) {
    		if (!contains(otherValue)) {
    			add((V) otherValue.clone());
    		}
    	}
    }

    public ItemDelta<V,D> diff(Item<V,D> other) {
    	return diff(other, ParameterizedEquivalenceStrategy.DEFAULT_FOR_DIFF);
    }

    public ItemDelta<V,D> diff(Item<V,D> other, @NotNull ParameterizedEquivalenceStrategy strategy) {
    	List<? extends ItemDelta> itemDeltas = new ArrayList<>();
		diffInternal(other, itemDeltas, strategy);
		if (itemDeltas.isEmpty()) {
			return null;
		}
		if (itemDeltas.size() > 1) {
			throw new UnsupportedOperationException("Item multi-delta diff is not supported yet");
		}
		return itemDeltas.get(0);
    }

	void diffInternal(Item<V, D> other, Collection<? extends ItemDelta> deltas,
			ParameterizedEquivalenceStrategy strategy) {
    	ItemDelta delta = createDelta();
    	if (other == null) {
    		if (delta.getDefinition() == null && this.getDefinition() != null) {
    			delta.setDefinition(this.getDefinition().clone());
    		}
    		//other doesn't exist, so delta means delete all values
            for (PrismValue value : getValues()) {
            	PrismValue valueClone = value.clone();
                delta.addValueToDelete(valueClone);
            }
    	} else {
    		if (delta.getDefinition() == null && other.getDefinition() != null) {
    			delta.setDefinition(other.getDefinition().clone());
		    }
    		// the other exists, this means that we need to compare the values one by one
    		Collection<PrismValue> outstandingOtherValues = new ArrayList<>(other.getValues().size());
    		outstandingOtherValues.addAll(other.getValues());
    		for (PrismValue thisValue : getValues()) {
    			Iterator<PrismValue> iterator = outstandingOtherValues.iterator();
    			boolean found = false;
    			while (iterator.hasNext()) {
    				PrismValueImpl otherValue = (PrismValueImpl) iterator.next();
    				if (thisValue.representsSameValue(otherValue, true)) {
    					found = true;
    					// Matching IDs, look inside to figure out internal deltas
					    ((PrismValueImpl) thisValue).diffMatchingRepresentation(otherValue, deltas, strategy);
    					// No need to process this value again
    					iterator.remove();
    					break;
					} else if (thisValue.equals(otherValue, strategy)) {
    					found = true;
    					// same values. No delta
    					// No need to process this value again
    					iterator.remove();
    					break;
					}
    			}
				if (!found) {
					// We have the value and the other does not, this is delete of the entire value
					delta.addValueToDelete(thisValue.clone());
				}
            }
    		// outstandingOtherValues are those values that the other has and we could not
    		// match them to any of our values. These must be new values to add
    		for (PrismValue outstandingOtherValue : outstandingOtherValues) {
    			delta.addValueToAdd(outstandingOtherValue.clone());
            }
    		// Some deltas may need to be polished a bit. E.g. transforming
    		// add/delete delta to a replace delta.
    		delta = fixupDelta(delta, other);
    	}
    	if (delta != null && !delta.isEmpty()) {
    		((Collection)deltas).add(delta);
    	}
    }

	protected ItemDelta<V,D> fixupDelta(ItemDelta<V, D> delta, Item<V, D> other) {
		return delta;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
		for (PrismValue value: getValues()) {
			value.accept(visitor);
		}
	}

	@Override
	public void accept(Visitor visitor, ItemPath path, boolean recursive) {
		// This implementation is supposed to only work for non-hierarchical items, such as properties and references.
		// hierarchical items must override it.
		if (recursive) {
			accept(visitor);
		} else {
			visitor.visit(this);
		}
	}
	
	/**
	 * Re-apply PolyString (and possible other) normalizations to the object.
	 */
	public void recomputeAllValues() {
		accept(visitable -> {
			if (visitable instanceof PrismPropertyValue<?>) {
				((PrismPropertyValue<?>)visitable).recompute(getPrismContext());
			}
		});
	}

	public void filterValues(Function<V, Boolean> function) {
		Iterator<V> iterator = values.iterator();
		while (iterator.hasNext()) {
			Boolean keep = function.apply(iterator.next());
			if (keep == null || !keep) {
				iterator.remove();
			}
		}
	}

	public void applyDefinition(D definition) throws SchemaException {
		applyDefinition(definition, true);
	}

	public void applyDefinition(D definition, boolean force) throws SchemaException {
		checkMutability();					// TODO consider if there is real change
		if (definition != null) {
			checkDefinition(definition);
		}
		if (this.prismContext == null && definition != null) {
			this.prismContext = definition.getPrismContext();
		}
		this.definition = definition;
		for (PrismValue pval: getValues()) {
			pval.applyDefinition(definition, force);
		}
	}

    public void revive(PrismContext prismContext) throws SchemaException {
        // it is necessary to do e.g. PolyString recomputation even if PrismContext is set
    	if (this.prismContext == null) {
            this.prismContext = prismContext;
            if (definition != null) {
                definition.revive(prismContext);
            }
        }
		for (V value: values) {
			value.revive(prismContext);
		}
	}

    protected void copyValues(CloneStrategy strategy, ItemImpl clone) {
        clone.elementName = this.elementName;
        clone.definition = this.definition;
        clone.prismContext = this.prismContext;
        // Do not clone parent so the cloned item can be safely placed to
        // another item
        clone.parent = null;
        clone.userData = MiscUtil.cloneMap(this.userData);
        clone.incomplete = this.incomplete;
		// Also do not copy 'immutable' flag so the clone is free to be modified
    }

    protected void propagateDeepCloneDefinition(boolean ultraDeep, D clonedDefinition, Consumer<ItemDefinition> postCloneAction) {
    	// nothing to do by default
    }

    public void checkConsistence(boolean requireDefinitions, ConsistencyCheckScope scope) {
    	checkConsistenceInternal(this, requireDefinitions, false, scope);
    }

    public void checkConsistence(boolean requireDefinitions, boolean prohibitRaw) {
        checkConsistenceInternal(this, requireDefinitions, prohibitRaw, ConsistencyCheckScope.THOROUGH);
    }

    public void checkConsistence(boolean requireDefinitions, boolean prohibitRaw, ConsistencyCheckScope scope) {
    	checkConsistenceInternal(this, requireDefinitions, prohibitRaw, scope);
    }

    public void checkConsistence() {
    	checkConsistenceInternal(this, false, false, ConsistencyCheckScope.THOROUGH);
    }

    public void checkConsistence(ConsistencyCheckScope scope) {
        checkConsistenceInternal(this, false, false, scope);
    }


    public void checkConsistenceInternal(Itemable rootItem, boolean requireDefinitions, boolean prohibitRaw, ConsistencyCheckScope scope) {
    	ItemPath path = getPath();
    	if (elementName == null) {
    		throw new IllegalStateException("Item "+this+" has no name ("+path+" in "+rootItem+")");
    	}

    	if (definition != null) {
    		checkDefinition(definition);
    	} else if (requireDefinitions && !isRaw()) {
    		throw new IllegalStateException("No definition in item "+this+" ("+path+" in "+rootItem+")");
    	}
		for (V val: values) {
			if (prohibitRaw && val.isRaw()) {
				throw new IllegalStateException("Raw value "+val+" in item "+this+" ("+path+" in "+rootItem+")");
			}
			if (val == null) {
				throw new IllegalStateException("Null value in item "+this+" ("+path+" in "+rootItem+")");
			}
			if (val.getParent() == null) {
				throw new IllegalStateException("Null parent for value "+val+" in item "+this+" ("+path+" in "+rootItem+")");
			}
			if (val.getParent() != this) {
				throw new IllegalStateException("Wrong parent for value "+val+" in item "+this+" ("+path+" in "+rootItem+"), "+
						"bad parent: " + val.getParent());
			}
			val.checkConsistenceInternal(rootItem, requireDefinitions, prohibitRaw, scope);
		}
	}

	protected abstract void checkDefinition(D def);

    public void assertDefinitions() throws SchemaException {
    	assertDefinitions("");
    }

	public void assertDefinitions(String sourceDescription) throws SchemaException {
		assertDefinitions(false, sourceDescription);
	}

	public void assertDefinitions(boolean tolarateRawValues, String sourceDescription) throws SchemaException {
		if (tolarateRawValues && isRaw()) {
			return;
		}
		if (definition == null) {
			throw new SchemaException("No definition in "+this+" in "+sourceDescription);
		}
	}

	/**
	 * Returns true is all the values are raw.
	 */
	public boolean isRaw() {
		for (V val: getValues()) {
			if (!val.isRaw()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true is at least one of the values is raw.
	 */
	public boolean hasRaw() {
		for (V val: getValues()) {
			if (val.isRaw()) {
				return true;
			}
		}
		return false;
	}

	public boolean isEmpty() {
	    return hasNoValues();
    }

    public boolean hasNoValues() {
		return getValues().isEmpty();
    }

    @Override
    public int hashCode(@NotNull EquivalenceStrategy equivalenceStrategy) {
		return equivalenceStrategy.hashCode(this);
    }

    @Override
    public int hashCode(@NotNull ParameterizedEquivalenceStrategy equivalenceStrategy) {
	    int valuesHash = MiscUtil.unorderedCollectionHashcode(values, null);
	    if (valuesHash == 0) {
		    // empty or non-significant container. We do not want this to destroy hashcode of
		    // parent item
		    return 0;
	    }
	    final int prime = 31;
	    int result = 1;
	    if (equivalenceStrategy.isConsideringElementNames()) {
		    String localElementName = elementName != null ? elementName.getLocalPart() : null;
		    result = prime * result + ((localElementName == null) ? 0 : localElementName.hashCode());
	    }
	    result = prime * result + valuesHash;
	    return result;
    }

	@Override
	public int hashCode() {
		return hashCode(getEqualsHashCodeStrategy());
	}

	@NotNull
	protected EquivalenceStrategy getEqualsHashCodeStrategy() {
		return defaultIfNull(defaultEquivalenceStrategy, ParameterizedEquivalenceStrategy.DEFAULT_FOR_EQUALS);
	}

	public boolean equals(Object obj, @NotNull EquivalenceStrategy strategy) {
		if (!(obj instanceof Item)) {
			return false;
		} else if (strategy instanceof ParameterizedEquivalenceStrategy) {
			// note that the counter is increased in the called equals(..) method - because that method can be called also
			// independently from this site
			return equals(obj, (ParameterizedEquivalenceStrategy) strategy);
		} else {
			incrementObjectCompareCounterIfNeeded(obj);
			return strategy.equals(this, (Item<?, ?>) obj);
		}
	}

	public boolean equals(Object obj, @NotNull ParameterizedEquivalenceStrategy parameterizedEquivalenceStrategy) {
		incrementObjectCompareCounterIfNeeded(obj);
		if (this == obj) {
			return true;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ItemImpl<?,?> second = (ItemImpl<?,?>) obj;
		return (!parameterizedEquivalenceStrategy.isConsideringDefinitions() || Objects.equals(definition, second.definition)) &&
				(!parameterizedEquivalenceStrategy.isConsideringElementNames() || Objects.equals(elementName, second.elementName)) &&
				incomplete == second.incomplete &&
				MiscUtil.unorderedCollectionEquals(values, second.values, parameterizedEquivalenceStrategy::equals);
		// Do not compare parents at all. They are not relevant.
	}

	private void incrementObjectCompareCounterIfNeeded(Object obj) {
		if (this instanceof PrismObject && prismContext != null && prismContext.getMonitor() != null) {
			prismContext.getMonitor().recordPrismObjectCompareCount((PrismObject<? extends Objectable>) this, obj);
		}
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object obj) {
		return equals(obj, getEqualsHashCodeStrategy());
	}

	/**
	 * Returns true if this item is operational one that should be ignored
	 * for operational data-insensitive comparisons and hashCode functions.
	 */
	public boolean isOperational() {
		D def = getDefinition();
		if (def != null) {
			return def.isOperational();
		} else {
			return false;
		}
	}

	@Override
    public String toString() {
        return getClass().getSimpleName() + "(" + PrettyPrinter.prettyPrint(getElementName()) + ")";
    }

    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.indentDebugDump(sb, indent);
        if (DebugUtil.isDetailedDebugDump()) {
        	sb.append(getDebugDumpClassName()).append(": ");
        }
        sb.append(DebugUtil.formatElementName(getElementName()));
        return sb.toString();
    }

	/**
     * Return a human readable name of this class suitable for logs.
     */
    protected String getDebugDumpClassName() {
        return "Item";
    }

    protected void appendDebugDumpSuffix(StringBuilder sb) {
    	if (incomplete) {
    		sb.append(" (incomplete)");
    	}
    }

	public boolean isImmutable() {
		return immutable;
	}

	public void setImmutable(boolean immutable) {
		this.immutable = immutable;
		for (V value : getValues()) {
			value.setImmutable(immutable);
		}
	}

	protected void checkMutability() {
		if (immutable) {
			throw new IllegalStateException("An attempt to modify an immutable item: " + toString());
		}
	}

	public void checkImmutability() {
    	synchronized (this) {		// because of modifyUnfrozen
			if (!immutable) {
				throw new IllegalStateException("Item is not immutable even if it should be: " + this);
			}
		}
	}

	// should be always called on non-overlapping objects! (for the synchronization to work correctly)
	public void modifyUnfrozen(Runnable mutator) {
		synchronized (this) {
			boolean wasImmutable = immutable;
			if (wasImmutable) {
				setImmutable(false);
			}
			try {
				mutator.run();
			} finally {
				if (wasImmutable) {
					setImmutable(true);
				}
			}
		}
	}

	// should be always called on non-overlapping objects! (for the synchronization to work correctly)
	public void modifyUnfrozen(Consumer<Item<V, D>> mutator) {
		synchronized (this) {
			boolean wasImmutable = immutable;
			if (wasImmutable) {
				setImmutable(false);
			}
			try {
				mutator.accept(this);
			} finally {
				if (wasImmutable) {
					setImmutable(true);
				}
			}
		}
	}

	// Path may contain ambiguous segments (e.g. assignment/targetRef when there are more assignments)
	// Note that the path can contain name segments only (at least for now)
	@NotNull
	public Collection<PrismValue> getAllValues(ItemPath path) {
    	return values.stream()
			    .flatMap(v -> v.getAllValues(path).stream())
			    .collect(Collectors.toList());
	}

	@Override
	public abstract Item<V,D> clone();

	@Override
	public Long getHighestId() {
		Holder<Long> highest = new Holder<>();
		this.accept(visitable -> {
			if (visitable instanceof PrismContainerValue) {
				Long id = ((PrismContainerValue<?>) visitable).getId();
				if (id != null && (highest.isEmpty() || id > highest.getValue())) {
					highest.setValue(id);
				}
			}
		});
		return highest.getValue();
	}
}
