package com.jcdecaux.setl.annotation;

import com.jcdecaux.setl.internal.SchemaConverter;
import com.jcdecaux.setl.internal.StructAnalyser;
import com.jcdecaux.setl.storage.Compressor;
import com.jcdecaux.setl.storage.XZCompressor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * The annotation Compress indicates {@link StructAnalyser} to save the metadata of corresponding fields
 * into the output StructType object. All annotated columns will be compressed by {@link SchemaConverter}
 * during the saving process in SparkRepository
 * </p>
 *
 * <p>
 * By default, the compression algorithm is XZ with the default compression level (=6). You can define other compressor
 * by implementing <code>com.jcdecaux.datacorp.storage.Compressor</code> interface.
 * </p>
 */
@InterfaceStability.Stable
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Compress {

    Class<? extends Compressor> compressor() default XZCompressor.class;

}
