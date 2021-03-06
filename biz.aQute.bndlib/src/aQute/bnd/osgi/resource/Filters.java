package aQute.bnd.osgi.resource;

import org.osgi.framework.namespace.IdentityNamespace;

import aQute.bnd.version.VersionRange;

public class Filters {

	public static final String DEFAULT_VERSION_ATTR = IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE;

	/**
	 * Generate an LDAP-style version filter from a version range, e.g. {@code
	 * [1.0,2.0)} generates {@code (&(version>=1.0)(!(version>=2.0))}
	 *
	 * @param range
	 * @return The generated filter.
	 * @throws IllegalArgumentException If the supplied range is invalid.
	 */
	public static String fromVersionRange(String range) throws IllegalArgumentException {
		return fromVersionRange(range, DEFAULT_VERSION_ATTR);
	}

	/**
	 * Generate an LDAP-style version filter from a version range, using a
	 * specific attribute name for the version; for example can be used to
	 * generate a range using the {@code bundle-version} attribute such as
	 * {@code (&(bundle-version>=1.0)(!(bundle-version>=2.0))}.
	 *
	 * @param range
	 * @param versionAttr
	 * @return The generated filter
	 * @throws IllegalArgumentException If the supplied range is invalid.
	 */
	public static String fromVersionRange(String range, String versionAttr) throws IllegalArgumentException {
		if (range == null)
			return null;
		VersionRange parsedRange = new VersionRange(range);
		return parsedRange.toFilter(versionAttr);
	}

}
