package avrocompiler;

import com.sampullara.mustache.Mustache;

/**
 * TODO: Edit this
 * <p/>
 * User: sam
 * Date: 4/11/11
 * Time: 10:32 AM
 */
public abstract class NoEncodingMustache extends Mustache {

  // We are not generating HTML so we don't want any encoding.
  @Override
  public String encode(String value) {
    return value;
  }
}
