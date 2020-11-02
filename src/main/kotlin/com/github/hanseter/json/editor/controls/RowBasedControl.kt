package com.github.hanseter.json.editor.controls

import com.github.hanseter.json.editor.types.ModelControlSynchronizer
import com.github.hanseter.json.editor.types.TypeModel
import com.github.hanseter.json.editor.util.BindableJsonType
import javafx.scene.control.Control
import org.json.JSONObject

class RowBasedControl<T>(
        private val controlWithProperty: ControlWithProperty<T?>,
        override val model: TypeModel<T?, *>) : TypeControl {

    override val control: Control?
        get() = controlWithProperty.control
    override val childControls: List<TypeControl>
        get() = emptyList()

    private val synchronizer = ModelControlSynchronizer(controlWithProperty.property, model)

    init {
        controlWithProperty.control.isDisable = model.schema.readOnly
    }

    override fun bindTo(type: BindableJsonType) {
        model.bound = type
        val rawVal = type.getValue(model.schema)
        controlWithProperty.previewNull(isBoundToNull(rawVal))
        synchronizer.modelChanged()
        updateStyleClasses(rawVal)
    }

    private fun updateStyleClasses(rawVal: Any?) {
        controlWithProperty.control.styleClass.removeAll("has-null-value", "has-default-value")

        if (rawVal == JSONObject.NULL) {
            if ("has-null-value" !in controlWithProperty.control.styleClass) {
                controlWithProperty.control.styleClass += "has-null-value"
            }
        } else if (rawVal == null) {
            if (model.defaultValue != null) {
                if ("has-default-value" !in controlWithProperty.control.styleClass) {
                    controlWithProperty.control.styleClass += "has-default-value"
                }
            } else {
                if ("has-null-value" !in controlWithProperty.control.styleClass) {
                    controlWithProperty.control.styleClass += "has-null-value"
                }
            }
        }
    }

    private fun isBoundToNull(rawVal: Any?): Boolean = !isBoundToDefault(rawVal) && JSONObject.NULL == rawVal

    private fun isBoundToDefault(rawVal: Any?): Boolean = model.defaultValue != null && null == rawVal
}