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
package com.evolveum.midpoint.schema;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.*;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.ShortDumpable;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;

/**
 * @author semancik
 *
 */
public class SelectorOptions<T> implements Serializable, DebugDumpable, ShortDumpable {
	private static final long serialVersionUID = 1L;

	private ObjectSelector selector;
	private T options;

    //region Construction
	public SelectorOptions(ObjectSelector selector, T options) {
		super();
		this.selector = selector;
		this.options = options;
	}

	public SelectorOptions(T options) {
		super();
		this.selector = null;
		this.options = options;
	}

	public static <T> SelectorOptions<T> create(UniformItemPath path, T options) {
		return new SelectorOptions<>(new ObjectSelector(path), options);
	}

	public static <T> SelectorOptions<T> create(T options) {
		return new SelectorOptions<>(options);
	}

	public static <T> Collection<SelectorOptions<T>> createCollection(UniformItemPath path, T options) {
		Collection<SelectorOptions<T>> optionsCollection = new ArrayList<>(1);
		optionsCollection.add(create(path, options));
		return optionsCollection;
	}

	public static <T> Collection<SelectorOptions<T>> createCollection(T options) {
		Collection<SelectorOptions<T>> optionsCollection = new ArrayList<>(1);
		optionsCollection.add(new SelectorOptions<>(options));
		return optionsCollection;
	}

	public static <T> Collection<SelectorOptions<T>> createCollection(T options, UniformItemPath... paths) {
		Collection<SelectorOptions<T>> optionsCollection = new ArrayList<>(paths.length);
		for (UniformItemPath path: paths) {
			optionsCollection.add(create(path, options));
		}
		return optionsCollection;
	}

	// modifies existing options collection, or creates a new collection
	// if options for given path exist, reuses them; or creates new ones instead
	@Deprecated // use GetOperationOptionsBuilder
	public static <T> Collection<SelectorOptions<T>> set(Collection<SelectorOptions<T>> options, UniformItemPath path,
			Supplier<T> constructor, Consumer<T> setter) {
		if (options == null) {
			options = new ArrayList<>();
		}
		Collection<T> optionsForPath = findOptionsForPath(options, path);
		T option;
		if (optionsForPath.isEmpty()) {
			option = constructor.get();
			options.add(SelectorOptions.create(path, option));
		} else {
			option = optionsForPath.iterator().next();
		}
		setter.accept(option);
		return options;
	}
	//endregion

    //region Simple getters
    public ObjectSelector getSelector() {
        return selector;
    }

    public T getOptions() {
        return options;
    }
    //endregion

    //region Methods for accessing content (findRoot, hasToLoadPath, ...)
	@Nullable
	private UniformItemPath getItemPathOrNull() {
		return selector != null && selector.getPath() != null ? selector.getPath() : null;
	}

	@NotNull
	public UniformItemPath getItemPath(UniformItemPath emptyPath) {
		return ObjectUtils.defaultIfNull(getItemPathOrNull(), emptyPath);
	}

	/**
	 * Returns options that apply to the "root" object. I.e. options that have null selector, null path, empty path, ...
	 * Must return 'live object' that could be modified.
	 */
	public static <T> T findRootOptions(Collection<SelectorOptions<T>> options) {
		if (options == null) {
			return null;
		}
		for (SelectorOptions<T> oooption: options) {
			if (oooption.isRoot()) {
				return oooption.getOptions();
			}
		}
		return null;
	}

	public static <T> Collection<SelectorOptions<T>> updateRootOptions(Collection<SelectorOptions<T>> options, Consumer<T> updater, Supplier<T> newValueSupplier) {
		if (options == null) {
			options = new ArrayList<>();
		}
		T rootOptions = findRootOptions(options);
		if (rootOptions == null) {
			rootOptions = newValueSupplier.get();
			options.add(new SelectorOptions<>(rootOptions));
		}
		updater.accept(rootOptions);
		return options;
	}

	/**
	 * Finds all the options for given path. TODO could there be more than one?
	 * Returns live objects that could be modified by client.
	 */
	@NotNull
	public static <T> Collection<T> findOptionsForPath(Collection<SelectorOptions<T>> options, @NotNull UniformItemPath path) {
		Collection<T> rv = new ArrayList<>();
		for (SelectorOptions<T> oooption: CollectionUtils.emptyIfNull(options)) {
			if (path.equivalent(oooption.getItemPathOrNull())) {
				rv.add(oooption.getOptions());
			}
		}
		return rv;
	}

	public boolean isRoot() {
		UniformItemPath itemPathOrNull = getItemPathOrNull();
		return itemPathOrNull == null || itemPathOrNull.isEmpty();
	}

    // TODO find a better way to specify this
    private static final Set<ItemPath> PATHS_NOT_RETURNED_BY_DEFAULT = new HashSet<>(Arrays.asList(
		    ItemPath.create(UserType.F_JPEG_PHOTO),
		    ItemPath.create(TaskType.F_RESULT),
		    ItemPath.create(TaskType.F_SUBTASK),
		    ItemPath.create(TaskType.F_NODE_AS_OBSERVED),
		    ItemPath.create(TaskType.F_NEXT_RUN_START_TIMESTAMP),
		    ItemPath.create(TaskType.F_NEXT_RETRY_TIMESTAMP),
		    ItemPath.create(LookupTableType.F_ROW),
		    ItemPath.create(AccessCertificationCampaignType.F_CASE)));

	private static final Set<Class<?>> OBJECTS_NOT_RETURNED_FULLY_BY_DEFAULT = new HashSet<>(Arrays.asList(
			UserType.class, FocusType.class, AssignmentHolderType.class, ObjectType.class,
			TaskType.class, LookupTableType.class, AccessCertificationCampaignType.class
	));

	public static boolean isRetrievedFullyByDefault(Class<?> objectType) {
		return !OBJECTS_NOT_RETURNED_FULLY_BY_DEFAULT.contains(objectType);
	}

	public static boolean hasToLoadPath(ItemPath path, Collection<SelectorOptions<GetOperationOptions>> options) {
        List<SelectorOptions<GetOperationOptions>> retrieveOptions = filterRetrieveOptions(options);
        if (retrieveOptions.isEmpty()) {
            return !ItemPathCollectionsUtil.containsEquivalent(PATHS_NOT_RETURNED_BY_DEFAULT, path);
        }

        for (SelectorOptions<GetOperationOptions> option : retrieveOptions) {
            ObjectSelector selector = option.getSelector();
            if (selector != null) {
	            UniformItemPath selected = selector.getPath();
	            if (!isPathInSelected(path, selected)) {
	                continue;
	            }
            }

            RetrieveOption retrieveOption = option.getOptions().getRetrieve();
            for (ItemPath notByDefault : PATHS_NOT_RETURNED_BY_DEFAULT) {
                if (path.equivalent(notByDefault)) {
                    //this one is not retrieved by default
                    switch (retrieveOption) {
                        case INCLUDE:
                            return true;
                        case EXCLUDE:
                        case DEFAULT:
                        default:
                            return false;
                    }
                }
            }

            switch (retrieveOption) {
                case EXCLUDE:
                case DEFAULT:
                    return false;
                case INCLUDE:
                default:
                    return true;
            }
        }

        return false;
    }
    
    public static boolean isExplicitlyIncluded(UniformItemPath path, Collection<SelectorOptions<GetOperationOptions>> options) {
        List<SelectorOptions<GetOperationOptions>> retrieveOptions = filterRetrieveOptions(options);
        if (retrieveOptions.isEmpty()) {
            return false;
        }

        for (SelectorOptions<GetOperationOptions> option : retrieveOptions) {
            ObjectSelector selector = option.getSelector();
            if (selector != null) {
	            UniformItemPath selected = selector.getPath();
	            if (!isPathInSelected(path, selected)) {
	                continue;
	            }
            }

            RetrieveOption retrieveOption = option.getOptions().getRetrieve();
            switch (retrieveOption) {
                case INCLUDE:
                    return true;
                case EXCLUDE:
                case DEFAULT:
                default:
                    return false;
            }
        }

        return false;
    }

    private static boolean isPathInSelected(ItemPath path, ItemPath selected) {
        if (selected == null || path == null) {
            return false;
        } else {
        	return selected.isSubPathOrEquivalent(path);
        }
    }

    public static List<SelectorOptions<GetOperationOptions>> filterRetrieveOptions(
            Collection<SelectorOptions<GetOperationOptions>> options) {
        List<SelectorOptions<GetOperationOptions>> retrieveOptions = new ArrayList<>();
        if (options == null) {
            return retrieveOptions;
        }

        for (SelectorOptions<GetOperationOptions> option : options) {
            if (option.getOptions() == null || option.getOptions().getRetrieve() == null) {
                continue;
            }

            retrieveOptions.add(option);
        }

        return retrieveOptions;
    }

	public static <T> Map<T, Collection<UniformItemPath>> extractOptionValues(Collection<SelectorOptions<GetOperationOptions>> options,
			Function<GetOperationOptions, T> supplier, PrismContext prismContext) {
		Map<T, Collection<UniformItemPath>> rv = new HashMap<>();
		final UniformItemPath EMPTY_PATH = prismContext.emptyPath();
		for (SelectorOptions<GetOperationOptions> selectorOption : CollectionUtils.emptyIfNull(options)) {
			T value = supplier.apply(selectorOption.getOptions());
			if (value != null) {
				Collection<UniformItemPath> itemPaths = rv.computeIfAbsent(value, t -> new HashSet<>());
				itemPaths.add(selectorOption.getItemPath(EMPTY_PATH));
			}
		}
		return rv;
	}

	//endregion

    //region hashCode, equals, toString
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((options == null) ? 0 : options.hashCode());
        result = prime * result + ((selector == null) ? 0 : selector.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SelectorOptions other = (SelectorOptions) obj;
        if (options == null) {
            if (other.options != null)
                return false;
        } else if (!options.equals(other.options))
            return false;
        if (selector == null) {
            if (other.selector != null)
                return false;
        } else if (!selector.equals(other.selector))
            return false;
        return true;
    }

    @Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ObjectOperationOptions(");
		shortDump(sb);
		sb.append(")");
		return sb.toString();
	}

	@Override
	public String debugDump(int indent) {
		return toString();
	}

	@Override
	public void shortDump(StringBuilder sb) {
		if (selector == null) {
			sb.append("/");
		} else {
			selector.shortDump(sb);
		}
		sb.append(":");
		if (options == null) {
			sb.append("null");
		} else if (options instanceof ShortDumpable) {
			((ShortDumpable)options).shortDump(sb);
		} else {
			sb.append(options);
		}
	}
	//endregion
}
