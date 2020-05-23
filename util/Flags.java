package util;

/**
 * Namespaced, fail-fast flags.
 * Usage:
 *
 *   // Required flags have no default specified.
 *   Flags flags = new Flags(Main.class);
 *   String first = flags.get("first"); // -Dfirst=john
 *   String last = flags.get("last"); // if unset, will throw IllegalArgumentException
 *
 * @author Pablo Mayrgundter
 */
public class Flags {

  String propName(Class clazz, String name) {
    return clazz.getName() + "." + name;
  }

  // Boolean.
  public boolean bool(Class clazz, String name) {
    return Boolean.getBoolean(propName(clazz, name));
  }

  // String flags.

  public String get(String name) {
    final String propName = propName(clazz, name);
    final String val = get(propName, propName, null);
    if (val == null) {
      throw new IllegalArgumentException("Missing required flag: " + propName);
    }
    return val;
  }

  public String get(String name, String defaultVal) {
    final String propName = propName(clazz, name);
    return get(propName, propName, defaultVal);
  }

  // Int flags.

  public int getInt(String name) {
    return Integer.parseInt(get(name, "0"));
  }

  public int getInt(String name, int defaultVal) {
    String strVal = get(name);
    if (strVal != null) {
      return Integer.parseInt(strVal);
    }
    return defaultVal;
  }

  // Boolean flags.

  public boolean getBool(String name) {
    return Boolean.parseBoolean(get(name, "false"));
  }

  public boolean getBool(String name, boolean defaultVal) {
    final String strVal = get(name);
    if (strVal != null) {
      return Boolean.parseBoolean(strVal);
    }
    return defaultVal;
  }

  // Double flags.

  public double getDouble(String name) {
    return Double.parseDouble(get(name, "0"));
  }

  public double getDouble(String name, double defaultVal) {
    final String strVal = get(name);
    if (strVal != null) {
      return Double.parseDouble(strVal);
    }
    return defaultVal;
  }

  Class clazz;

  public Flags(Class clazz) {
    this.clazz = clazz;
  }

  public <T> T get(String propName, T defVal) {
    return get(propName, null, defVal);
  }

  /**
   * Return type inference is done on the default value.  For
   * instance, if you want a long value, the default value must be
   * or be cast to a long.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String propName, String abbrevPropName, T defVal) {
    final String qName = propName(clazz, propName);
    String val = System.getProperty(qName);

    if (val == null && abbrevPropName != null) {
      val = System.getProperty(abbrevPropName);
    }

    if (val == null) {
      val = defVal + "";
    }

    System.out.println(qName + "=" + val);

    if (val == null)
      return defVal;
    else if (defVal instanceof Integer)
      return (T) Integer.valueOf(val);
    else if (defVal instanceof Long)
      return (T) Long.valueOf(val);
    else if (defVal instanceof Float)
      return (T) Float.valueOf(val);
    else if (defVal instanceof Double)
      return (T) Double.valueOf(val);
    else if (defVal instanceof Boolean)
      return (T) Boolean.valueOf(val);
    //      if (!(val instanceof defVal))
    //        throw new IllegalArgumentException("Illegal value for flag: " + val);
    return (T) val;
  }
}
