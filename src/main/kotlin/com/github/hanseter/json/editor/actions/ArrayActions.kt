package com.github.hanseter.json.editor.actions

import com.github.hanseter.json.editor.extensions.ArraySchemaWrapper
import com.github.hanseter.json.editor.types.SupportedType
import com.github.hanseter.json.editor.types.TypeModel
import org.json.JSONArray
import org.json.JSONObject

val arrayActions = listOf(AddToArrayAction, RemoveFromArrayAction, MoveArrayItemDownAction, MoveArrayItemUpAction)

object AddToArrayAction : EditorAction {
    override val text: String = "\uD83D\uDFA3"// \uD83D\uDFA3 = 🞣
    override val description: String = "Inserts a new empty item at the end of the list"
    override val selector: ActionTargetSelector = ActionTargetSelector.AllOf(listOf(
            ActionTargetSelector.ReadOnly.invert(),
            ActionTargetSelector { it.supportedType == SupportedType.ComplexType.ArrayType }))

    override fun apply(currentData: JSONObject, model: TypeModel<*, *>): JSONObject? {
        val children = (model as TypeModel<JSONArray?, SupportedType.ComplexType.ArrayType>).value
                ?: JSONArray()
        children.put(children.length(), JSONObject.NULL)
        return currentData
    }
}

object ArrayChildSelector : ActionTargetSelector {
    override fun matches(model: TypeModel<*, *>): Boolean =
            model.schema.let { it is ArraySchemaWrapper && !it.parent.readOnly }
}

object RemoveFromArrayAction : EditorAction {
    override val text: String = "-"
    override val description: String = "Remove this item"
    override val selector: ActionTargetSelector
        get() = ArrayChildSelector

    override fun apply(currentData: JSONObject, model: TypeModel<*, *>): JSONObject? {
        val children = model.schema.parent?.extractProperty(currentData) as? JSONArray
                ?: return null
        val index = (model.schema as ArraySchemaWrapper).index
        children.remove(index)
        return currentData
    }
}

object MoveArrayItemUpAction : EditorAction {
    override val text: String = "↑"
    override val description: String = "Move this item one row up"
    override val selector: ActionTargetSelector
        get() = ArrayChildSelector

    override fun apply(currentData: JSONObject, model: TypeModel<*, *>): JSONObject? {
        val children = model.schema.parent?.extractProperty(currentData) as? JSONArray
                ?: return null
        val index = (model.schema as ArraySchemaWrapper).index
        if (index == 0) return null
        val tmp = children.get(index - 1)
        children.put(index - 1, children.get(index))
        children.put(index, tmp)
        return currentData
    }
}

object MoveArrayItemDownAction : EditorAction {
    override val text: String = "↓"
    override val description: String = "Move this item one row down"
    override val selector: ActionTargetSelector
        get() = ArrayChildSelector

    override fun apply(currentData: JSONObject, model: TypeModel<*, *>): JSONObject? {
        val children = model.schema.parent?.extractProperty(currentData) as? JSONArray
                ?: return null
        val index = (model.schema as ArraySchemaWrapper).index
        if (index >= children.length() - 1) return null
        val tmp = children.get(index + 1)
        children.put(index + 1, children.get(index))
        children.put(index, tmp)
        return currentData
    }
}