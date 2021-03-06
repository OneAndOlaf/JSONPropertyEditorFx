package com.github.hanseter.json.editor.actions

import com.github.hanseter.json.editor.types.SupportedType
import com.github.hanseter.json.editor.types.TypeModel
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.ReferenceSchema
import org.everit.json.schema.Schema

/**
 * A target selector is used to specify whether for example an action or a validator should be applied against a specifier subpart of the schema.
 */
fun interface TargetSelector {

    fun matches(model: TypeModel<*, *>): Boolean

    fun invert(): TargetSelector = Inverted(this)

    class Custom(private val predicate: (TypeModel<*, *>) -> Boolean) : TargetSelector {
        override fun matches(model: TypeModel<*, *>) = predicate(model)
    }

    object Always : TargetSelector {
        override fun matches(model: TypeModel<*, *>) = true
    }

    class AllOf(private val selectors: List<TargetSelector>) : TargetSelector {
        override fun matches(model: TypeModel<*, *>) = selectors.all { it.matches(model) }
    }

    class AnyOf(private val selectors: List<TargetSelector>) : TargetSelector {
        override fun matches(model: TypeModel<*, *>) = selectors.any { it.matches(model) }
    }

    class Inverted(private val selector: TargetSelector) : TargetSelector {

        override fun matches(model: TypeModel<*, *>) = !selector.matches(model)

        override fun invert() = selector

    }

    class Single(private val jsonPointerFragment: String) : TargetSelector {

        private fun getFragmentFromJsonPointer(pointer: String): String? {
            val index = pointer.indexOf('#')

            return if (index > -1) pointer.substring(index + 1) else null
        }

        override fun matches(model: TypeModel<*, *>): Boolean {
            val location = model.schema.schemaLocation ?: return false
            val schemaPointer = getFragmentFromJsonPointer(location)

            return schemaPointer == this.jsonPointerFragment
        }
    }

    object Required : TargetSelector {

        override fun matches(model: TypeModel<*, *>): Boolean {
            /*
             this differs from schema.required in that this returns true if the actual status cannot
             be determined while schema.required returns false.

             Many custom actions expect it to be true in those cases, so this method should not
             simply be reduced to querying schema.required.

             It may be prudent to make this selector configurable so the API user can decide whether
             they want it to apply strictly or leniently.
             */

            val schema = model.schema.objectAncestor?.baseSchema?.let { getReferredSchema(it) }

            return (schema as? ObjectSchema)?.let {
                model.schema.propertyName in it.requiredProperties
            } ?: true
        }

    }

    object ReadOnly : TargetSelector {
        override fun matches(model: TypeModel<*, *>) =
                model.schema.readOnly
    }

    class SchemaType(private vararg val types: SupportedType<*>) : TargetSelector {

        override fun matches(model: TypeModel<*, *>): Boolean =
                types.contains(model.supportedType)

    }

    class HasCustomField(private val fieldName: String, private val value: Any? = null) : TargetSelector {

        private fun matches(schema: Schema): Boolean =
                schema.unprocessedProperties.containsKey(fieldName)
                        && (null == value || schema.unprocessedProperties[fieldName] == value)

        override fun matches(model: TypeModel<*, *>): Boolean =
                matches(model.schema.baseSchema) || matches(model.schema.schemaForValidation)

    }

}

/**
 * Gets the referred schema of a reference schema.
 *
 * @return the referred schema, `schema` if it is not a reference schema, or `null` if `schema` or any referred schema is `null`
 */
fun getReferredSchema(schema: Schema): Schema {
    var currentSchema = schema

    while (currentSchema is ReferenceSchema) {
        currentSchema = currentSchema.referredSchema
    }

    return currentSchema
}