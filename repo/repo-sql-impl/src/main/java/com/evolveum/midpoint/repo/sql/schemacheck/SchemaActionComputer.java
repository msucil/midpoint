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

package com.evolveum.midpoint.repo.sql.schemacheck;

import com.evolveum.midpoint.common.LocalizationService;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration.MissingSchemaAction;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration.UpgradeableSchemaAction;
import com.evolveum.midpoint.repo.sql.data.common.RGlobalMetadata;
import com.evolveum.midpoint.repo.sql.helpers.BaseHelper;
import com.evolveum.midpoint.util.LocalizableMessageBuilder;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Determines the action that should be done against the database (none, stop, warn, create, upgrade)
 *
 * Takes the following input:
 * - information about the database schema state (tables existence/non-existence, declared schema version)
 * - repository configuration (namely actions for missing/upgradeable/incompatible schemas)
 *
 * TODOs (issues to consider)
 * 1) database schema version could differ from major midPoint version (e.g. 3.7.2 vs 3.7 - fortunately it's history)
 * 2) check if db variant is applicable (e.g. upgrading "plain" mysql using "utf8mb4" variant and vice versa)
 *
 * @author mederly
 */

@Component
class SchemaActionComputer {

	private static final Trace LOGGER = TraceManager.getTrace(SchemaActionComputer.class);

	private static final String RELEASE_NOTES_URL_PREFIX = "https://wiki.evolveum.com/display/midPoint/Release+";
	private static final String SQL_SCHEMA_SCRIPTS_URL = "https://wiki.evolveum.com/display/midPoint/SQL+Schema+Scripts";
	
	private static final Pattern VERSION_SUFFIX_PATTERN = Pattern.compile("(.*)-[^.]+");

	@Autowired private LocalizationService localizationService;
	@Autowired private BaseHelper baseHelper;

	@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
	private static Set<Pair<String, String>> AUTOMATICALLY_UPGRADEABLE = new HashSet<>(
			Arrays.asList(new ImmutablePair<>("3.8", "3.9"))
	);

	enum State {
		COMPATIBLE, NO_TABLES, AUTOMATICALLY_UPGRADEABLE, MANUALLY_UPGRADEABLE, INCOMPATIBLE
	}

	@NotNull
	SchemaAction determineSchemaAction(SchemaState schemaState) {
		LOGGER.debug("Determining action for state: {}", schemaState);
		State state = determineState(schemaState);
		switch (state) {
			case COMPATIBLE:
				return new SchemaAction.None();
			case NO_TABLES:
				return determineActionForNoTables(schemaState);
			case AUTOMATICALLY_UPGRADEABLE:
				return determineActionForAutomaticallyUpgradeableSchema(schemaState);
			case MANUALLY_UPGRADEABLE:
				return determineActionForManuallyUpgradeableSchema(schemaState);
			case INCOMPATIBLE:
				return determineActionForIncompatibleSchema(schemaState);
			default: throw new AssertionError(state);
		}
	}

	@NotNull
	private SchemaAction determineActionForNoTables(SchemaState schemaState) {
		MissingSchemaAction action = getMissingSchemaAction();
		switch (action) {
			case CREATE:
				return new SchemaAction.CreateSchema(determineCreateScriptFileName());
			case WARN:
				return new SchemaAction.Warn("Database schema is missing or inaccessible. Please resolve the situation immediately.");
			case STOP:
				return new SchemaAction.Stop( "Database schema is missing or inaccessible. Please resolve the situation immediately.\n"
						+ "You can either run the SQL scripts manually (see " + SQL_SCHEMA_SCRIPTS_URL + ")\nor you can set the '"
						+ SqlRepositoryConfiguration.PROPERTY_MISSING_SCHEMA_ACTION + "' configuration property to '"
						+ MissingSchemaAction.CREATE.getValue() + "' and midPoint will do that for you.",
						schemaState.dataStructureCompliance.validationException);
			default: throw new AssertionError(action);
		}
	}

	@NotNull
	private SchemaAction determineActionForManuallyUpgradeableSchema(SchemaState state) {
		String message = "Database schema is not compatible with the executing code; however, an upgrade path is available.\n"
				+ getCurrentAndRequiredVersionInformation(state)
				+ "For more information about the upgrade process please see "
				+ RELEASE_NOTES_URL_PREFIX + getRequiredDatabaseSchemaVersion();
		UpgradeableSchemaAction action = getUpgradeableSchemaAction();
		switch (action) {
			case WARN:
				return new SchemaAction.Warn(message);
			case STOP:
			case UPGRADE:
				return new SchemaAction.Stop(message, state.dataStructureCompliance.validationException);
			default: throw new AssertionError(action);
		}
	}

	@NotNull
	private SchemaAction determineActionForAutomaticallyUpgradeableSchema(SchemaState state) {
		UpgradeableSchemaAction action = getUpgradeableSchemaAction();
		if (action == UpgradeableSchemaAction.UPGRADE) {
			String from = state.declaredVersion.version;
			String to = getRequiredDatabaseSchemaVersion();
			return new SchemaAction.UpgradeSchema(determineUpgradeScriptFileName(from, to), from, to);
		}
		String message = "Database schema is not compatible with the executing code; however, an upgrade path is available.\n"
				+ getCurrentAndRequiredVersionInformation(state)
				+ "For more information about the upgrade process please see "
				+ RELEASE_NOTES_URL_PREFIX + getRequiredDatabaseSchemaVersion() + ".\n\n"
				+ "You can even request automatic upgrade by setting '" + SqlRepositoryConfiguration.PROPERTY_UPGRADEABLE_SCHEMA_ACTION + "' "
				+ "property to '" + UpgradeableSchemaAction.UPGRADE.getValue() + "'.";
		//noinspection Duplicates
		switch (action) {
			case WARN:
				return new SchemaAction.Warn(message);
			case STOP:
				return new SchemaAction.Stop(message, state.dataStructureCompliance.validationException);
			default: throw new AssertionError(action);
		}
	}

	@NotNull
	private SchemaAction determineActionForIncompatibleSchema(SchemaState state) {
		String message =
				"Database schema is not compatible with the executing code.\n"
						+ getCurrentAndRequiredVersionInformation(state)
						+ "Please resolve this situation immediately.";
		if (getRequiredDatabaseSchemaVersion().equals(state.declaredVersion.version) &&
				state.dataStructureCompliance.state == DataStructureCompliance.State.NOT_COMPLIANT) {
			message += "\n\nAlthough the declared schema version matches the required version, the validation of the schema "
					+ "did not pass. This may indicate corrupted or inaccessible (parts of) the database schema.";
			if (state.dataStructureCompliance.validationException != null) {
				message += "\nValidation result: " + state.dataStructureCompliance.validationException.getMessage();
			}
		}
		SqlRepositoryConfiguration.IncompatibleSchemaAction action = getIncompatibleSchemaAction();
		//noinspection Duplicates
		switch (action) {
			case WARN:
				return new SchemaAction.Warn(message);
			case STOP:
				return new SchemaAction.Stop(message, state.dataStructureCompliance.validationException);
			default: throw new AssertionError(action);
		}
	}

	@NotNull
	private String getCurrentAndRequiredVersionInformation(SchemaState state) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		if (state.declaredVersion.version != null) {
			sb.append("Current version is: ").append(state.declaredVersion.version);
		} else {
			sb.append("Current version cannot be determined.");
			switch (state.declaredVersion.state) {
				case METADATA_TABLE_MISSING:
					sb.append(" The metadata table (" + RGlobalMetadata.TABLE_NAME + ") is missing or inaccessible. This indicates the version is below 3.9.");
					break;
				case VERSION_VALUE_MISSING:
					sb.append(" The metadata table (" + RGlobalMetadata.TABLE_NAME + ") exists but it does not contain version information.");
					break;
			}
		}
		sb.append("\nRequired version is: ").append(getRequiredDatabaseSchemaVersion()).append("\n\n");
		return sb.toString();
	}

	private State determineState(SchemaState schemaState) {
		@NotNull String requiredVersion = getRequiredDatabaseSchemaVersion();
		DataStructureCompliance.State dataComplianceState = schemaState.dataStructureCompliance.state;
		DeclaredVersion.State versionState = schemaState.declaredVersion.state;
		if (requiredVersion.equals(schemaState.declaredVersion.version)) {
			switch (dataComplianceState) {
				case COMPLIANT:
					return State.COMPATIBLE;
				case NO_TABLES:
					throw new AssertionError("No tables but schema version declared?");
				case NOT_COMPLIANT:
					LOGGER.warn("Strange: Declared version matches but the table structure is not compliant. Please investigate this.");
					return State.INCOMPATIBLE;
				default: throw new AssertionError(dataComplianceState);
			}
		} else {
			switch (dataComplianceState) {
				case NO_TABLES:
					return State.NO_TABLES;
				case COMPLIANT:
					if (versionState == DeclaredVersion.State.METADATA_TABLE_MISSING) {
						LOGGER.warn("Strange: Data structure is compliant but metadata table is missing or inaccessible. Please investigate this.");
						return State.INCOMPATIBLE;
					} else if (versionState == DeclaredVersion.State.VERSION_VALUE_MISSING) {
						LOGGER.warn("Data structure is compliant but version information is missing from the global metadata table. Please investigate and fix this.");
						return State.COMPATIBLE;    // let's continue
					}
					return determineUpgradeability(schemaState.declaredVersion, requiredVersion);
				case NOT_COMPLIANT:
					if (versionState == DeclaredVersion.State.METADATA_TABLE_MISSING) {
						return State.MANUALLY_UPGRADEABLE;           // this is currently true (any version can be upgraded to 3.9)
					} else if (versionState == DeclaredVersion.State.VERSION_VALUE_MISSING) {
						return State.INCOMPATIBLE;              // something strange happened; this does not seem to be an upgrade situation
					}
					return determineUpgradeability(schemaState.declaredVersion, requiredVersion);
				default:
					throw new AssertionError(dataComplianceState);
			}
		}
	}

	private State determineUpgradeability(DeclaredVersion declaredVersionInfo, String requiredVersion) {
		String declaredVersion = declaredVersionInfo.version;
		assert declaredVersion != null;
		assert requiredVersion != null;
		assert !requiredVersion.equals(declaredVersion);

		int comparison = compareVersions(declaredVersion, requiredVersion);
		if (comparison == 0) {
			throw new AssertionError("Versions are different but comparison yields 0: " + declaredVersion + " vs " + requiredVersion);
		} else if (comparison > 0) {
			// declared > expected
			return State.INCOMPATIBLE;
		} else {
			// declared < expected
			if (AUTOMATICALLY_UPGRADEABLE.contains(new ImmutablePair<>(declaredVersion, requiredVersion))) {
				return State.AUTOMATICALLY_UPGRADEABLE;
			} else {
				return State.MANUALLY_UPGRADEABLE;
			}
		}
	}

	@NotNull
	private UpgradeableSchemaAction getUpgradeableSchemaAction() {
		return baseHelper.getConfiguration().getUpgradeableSchemaAction();
	}

	@NotNull
	private SqlRepositoryConfiguration.IncompatibleSchemaAction getIncompatibleSchemaAction() {
		return baseHelper.getConfiguration().getIncompatibleSchemaAction();
	}

	@NotNull
	private MissingSchemaAction getMissingSchemaAction() {
		return baseHelper.getConfiguration().getMissingSchemaAction();
	}

	/**
	 * Currently this is equal to midPoint major version. But, in general, there might be differences, e.g.
	 * - no database change between major mP versions: e.g. if 5.7 would have the same db as 5.6, then req.schema version for 5.7 would be "5.6"
	 * - database change between minor versions: e.g. 3.7 and 3.7.1 have version of "3.7", but 3.7.2 has version of "3.7.2"
	 * (fortunately, at that time we didn't have repository versions!)
	 */
	@NotNull
	private String getRequiredDatabaseSchemaVersion() {
		// TODO adapt if major mp version != database schema version
		return getMajorMidPointVersion();
	}

	@NotNull
	private String getMajorMidPointVersion() {
		String version = localizationService
				.translate(LocalizableMessageBuilder.buildKey("midPointVersion"), Locale.getDefault());
		String noSnapshot = removeSuffix(version);
		int firstDot = noSnapshot.indexOf('.');
		if (firstDot < 0) {
			throw new SystemException("Couldn't determine midPoint version from '" + version + "'");
		}
		int secondDot = noSnapshot.indexOf('.', firstDot+1);
		if (secondDot < 0) {
			return noSnapshot;
		} else {
			return noSnapshot.substring(0, secondDot);
		}
	}

	private String removeSuffix(String version) {
		Matcher matcher = VERSION_SUFFIX_PATTERN.matcher(version);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return version;
	}

	private String determineUpgradeScriptFileName(@NotNull String from, @NotNull String to) {
		SqlRepositoryConfiguration.Database database = getDatabase();
		return database.name().toLowerCase() + "-upgrade-" + from + "-" + to + getVariantSuffix() + ".sql";
	}

	private String determineCreateScriptFileName() {
		SqlRepositoryConfiguration.Database database = getDatabase();
		String requiredVersion = getRequiredDatabaseSchemaVersion();
		return database.name().toLowerCase() + "-" + requiredVersion + "-all" + getVariantSuffix() + ".sql";
	}

	private String getVariantSuffix() {
		String variant = baseHelper.getConfiguration().getSchemaVariant();
		return variant != null ? "-" + variant : "";
	}

	@NotNull
	private SqlRepositoryConfiguration.Database getDatabase() {
		SqlRepositoryConfiguration.Database database = baseHelper.getConfiguration().getDatabase();
		if (database == null) {
			throw new SystemException("Couldn't create/upgrade DB schema because database kind is not known");
		}
		if (database == SqlRepositoryConfiguration.Database.MARIADB) {
			database = SqlRepositoryConfiguration.Database.MYSQL;
		}
		return database;
	}

	private int compareVersions(String version1, String version2) {
		PrimitiveIterator.OfInt parts1 = toParts(version1);
		PrimitiveIterator.OfInt parts2 = toParts(version2);

		for (;;) {
			if (!parts1.hasNext()) {
				if (parts2.hasNext()) {
					return -1;
				} else {
					return 0;
				}
			} else if (!parts2.hasNext()) {
				return 1;
			}
			int next1 = parts1.next();
			int next2 = parts2.next();
			if (next1 != next2) {
				return Integer.compare(next1, next2);
			}
		}
	}

	private PrimitiveIterator.OfInt toParts(String s) {
		return Arrays.stream(s.split("\\.")).mapToInt(Integer::parseInt).iterator();
	}
}
