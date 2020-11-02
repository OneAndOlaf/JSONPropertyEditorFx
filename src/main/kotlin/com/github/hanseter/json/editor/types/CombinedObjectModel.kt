package com.github.hanseter.json.editor.types

import com.github.hanseter.json.editor.extensions.SchemaWrapper
import com.github.hanseter.json.editor.util.BindableJsonType
import org.everit.json.schema.CombinedSchema
import org.json.JSONObject

class CombinedObjectModel(override val schema: SchemaWrapper<CombinedSchema>) : TypeModel<JSONObject?, SupportedType.ComplexType.ObjectType> {
    override val supportedType: SupportedType.ComplexType.ObjectType
        get() = SupportedType.ComplexType.ObjectType
    override var bound: BindableJsonType? = null
    override val defaultValue: JSONObject?
        get() = schema.schema.defaultValue as? JSONObject

    override var value: JSONObject?
        get() = bound?.let { BindableJsonType.convertValue(it.getValue(schema), schema, CONVERTER) }
        set(value) {
            bound?.setValue(schema, value)
        }

    companion object {
        val CONVERTER: (Any?) -> JSONObject? = { it as? JSONObject }
    }
}