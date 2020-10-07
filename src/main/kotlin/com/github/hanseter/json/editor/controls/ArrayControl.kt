package com.github.hanseter.json.editor.controls

import com.github.hanseter.json.editor.ControlFactory
import com.github.hanseter.json.editor.IdReferenceProposalProvider
import com.github.hanseter.json.editor.ResolutionScopeProvider
import com.github.hanseter.json.editor.actions.ActionTargetSelector
import com.github.hanseter.json.editor.actions.ChangeValueEditorAction
import com.github.hanseter.json.editor.actions.EditorAction
import com.github.hanseter.json.editor.extensions.FilterableTreeItem
import com.github.hanseter.json.editor.extensions.RegularSchemaWrapper
import com.github.hanseter.json.editor.extensions.SchemaWrapper
import com.github.hanseter.json.editor.extensions.TreeItemData
import com.github.hanseter.json.editor.util.BindableJsonArray
import com.github.hanseter.json.editor.util.BindableJsonArrayEntry
import com.github.hanseter.json.editor.util.BindableJsonType
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.Control
import javafx.scene.control.Tooltip
import org.controlsfx.control.decoration.Decorator
import org.controlsfx.validation.Severity
import org.controlsfx.validation.ValidationMessage
import org.controlsfx.validation.decoration.GraphicValidationDecoration
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.Schema
import org.json.JSONArray
import org.json.JSONObject

class ArrayControl(
        override val schema: SchemaWrapper<ArraySchema>,
        private val contentSchema: Schema,
        private val refProvider: IdReferenceProposalProvider,
        private val resolutionScopeProvider: ResolutionScopeProvider,
        private val actions: List<EditorAction>
) : TypeWithChildrenControl(schema, listOf()) {
    private val children = mutableListOf<TypeControl>()
    private var subArray: BindableJsonArray? = null
    private val itemCountValidationMessage = SimpleObjectProperty<ValidationMessage?>(null)
    private val uniqueItemValidationMessage = SimpleObjectProperty<ValidationMessage?>(null)
    private val validInternal = SimpleBooleanProperty(true)
    override val valid = SimpleBooleanProperty(true)
    private val onValidationStateChanged = InvalidationListener {
        redecorate()
        validInternal.set(itemCountValidationMessage.get() == null && uniqueItemValidationMessage.get() == null)
    }


    init {
        itemCountValidationMessage.addListener(onValidationStateChanged)
        uniqueItemValidationMessage.addListener(onValidationStateChanged)

        // \uD83D\uDFA3 = 🞣
        editorActionsContainer.addActionIfMatches(ChangeValueEditorAction("\uD83D\uDFA3", ActionTargetSelector.Always()) { _, _ ->
            addItemAt(children.lastIndex + 1)
        }.apply {
            ignoreReturnValue = true
            description = "Inserts a new empty item at the end of the list"
        }, schema)

        actions.forEach { editorActionsContainer.addActionIfMatches(it, schema) }
    }

    override fun bindTo(type: BindableJsonType) {
        bound = type
        subArray = createSubArray(type)
        updateChildCount()
        validateChildUniqueness()

        super.bindTo(type)
    }

    private fun createSubArray(parent: BindableJsonType): BindableJsonArray {
        var arr = parent.getValue(schema) as? JSONArray
        if (arr == null) {
            arr = JSONArray()
            parent.setValue(schema, arr)
        }
        return BindableJsonArray(parent, arr)
    }

    private fun updateChildCount() {
        val subArray = subArray
        var children = bound?.getValue(schema) as? JSONArray
        if (children == null) {
            children = JSONArray()
        }
        while (this.children.size > children.length()) {
            node.remove(this.children.removeAt(this.children.size - 1).node)
        }
        while (this.children.size < children.length()) {
            val new = ArrayChildWrapper(
                    ControlFactory.convert(RegularSchemaWrapper(schema, contentSchema,
                            this.children.size.toString()), refProvider, resolutionScopeProvider,
                            actions
                    ), !schema.readOnly
            )
            this.children.add(new)
            node.add(new.node)
        }
        if (subArray != null) {
            for (i in 0 until children.length()) {
                val obj = BindableJsonArrayEntry(subArray, i)
                this.children[i].bindTo(obj)
            }
        }
        bound?.setValue(schema, children)
        validateChildCount(children)
        valid.bind(validInternal.and(createValidityBinding(this.children)))
    }

    private inner class ArrayChildWrapper(wrapped: TypeControl, addArrayControls: Boolean) : TypeControl by wrapped {
        override val node: FilterableTreeItem<TreeItemData>

        private val arrayItemActions = listOf(
                ChangeValueEditorAction("-", ActionTargetSelector.Always()) { schema, value ->
                    removeItem(this@ArrayChildWrapper)
                }.apply {
                    ignoreReturnValue = true
                    description = "Remove this item"
                },
                ChangeValueEditorAction("↑", ActionTargetSelector.Always()) { schema, value ->
                    moveItemUp(this@ArrayChildWrapper)
                }.apply {
                    ignoreReturnValue = true
                    description = "Move this item one row up"
                },
                ChangeValueEditorAction("↓", ActionTargetSelector.Always()) { schema, value ->
                    moveItemDown(this@ArrayChildWrapper)
                }.apply {
                    ignoreReturnValue = true
                    description = "Move this item one row down"
                }
        )

        private val removeButton = Button("-").apply {
            tooltip = Tooltip("Remove this item")
            onAction = EventHandler { removeItem(this@ArrayChildWrapper) }
        }
        private val upButton = Button("↑").apply {
            tooltip = Tooltip("Move this item one row up")
            onAction = EventHandler { moveItemUp(this@ArrayChildWrapper) }
        }
        private val downButton = Button("↓").apply {
            tooltip = Tooltip("Move this item one row down")
            onAction = EventHandler { moveItemDown(this@ArrayChildWrapper) }
        }

        init {
            val origNode = wrapped.node
            val children = origNode.list.toList()
            origNode.clear()
            val origItemData = origNode.value
            val actions = ActionsContainer(this@ArrayChildWrapper, listOf())

            if (addArrayControls) {
                arrayItemActions.forEach { actions.addActionIfMatches(it, this@ArrayChildWrapper.schema) }
            }
            if (origItemData.action != null) {
                this@ArrayControl.actions.forEach { actions.addActionIfMatches(it, this@ArrayChildWrapper.schema) }
            }
            this.node = FilterableTreeItem(TreeItemData(origItemData.key, origItemData.description, origItemData.control, actions, origItemData.isRoot, origItemData.isHeadline))
            node.addAll(children)
        }

    }

    private fun redecorate() {
        Decorator.removeAllDecorations(this.node.value.control)
        val message = itemCountValidationMessage.get() ?: uniqueItemValidationMessage.get()
        if (message != null) {
            GraphicValidationDecoration().applyValidationDecoration(message)
        }
    }

    private fun validateChildCount(children: JSONArray) {
        itemCountValidationMessage.set(
                when {
                    hasTooManyItems(children.length()) -> SimpleValidationMessage(
                            this.node.value.control as Control,
                            "Must have at most " + schema.schema.maxItems + " items",
                            Severity.ERROR
                    )
                    hasTooFewItems(children.length()) -> SimpleValidationMessage(
                            this.node.value.control as Control,
                            "Must have at least " + schema.schema.minItems + " items",
                            Severity.ERROR
                    )
                    else -> null
                }
        )
    }

    class SimpleValidationMessage(
            private val target: Control,
            private val text: String,
            private val severity: Severity
    ) : ValidationMessage {
        override fun getTarget(): Control = target
        override fun getText(): String = text
        override fun getSeverity(): Severity = severity
    }

    private fun hasTooManyItems(childCount: Int) =
            schema.schema.maxItems != null && childCount > schema.schema.maxItems

    private fun hasTooFewItems(childCount: Int) =
            schema.schema.minItems != null && childCount < schema.schema.minItems

    private fun addItemAt(position: Int) {
        val children = bound?.getValue(schema) as? JSONArray ?: return
        children.put(position, JSONObject.NULL)
        updateChildCount()
        validateChildUniqueness()
    }

    private fun removeItem(toRemove: TypeControl) {
        val children = bound?.getValue(schema) as? JSONArray ?: return
        val index = this.children.indexOf(toRemove)
        children.remove(index)
        updateChildCount()
    }

    private fun moveItemUp(toMove: TypeControl) {
        val children = bound?.getValue(schema) as? JSONArray ?: return
        val index = this.children.indexOf(toMove)
        if (index == 0) return
        val tmp = children.get(index - 1)
        children.put(index - 1, children.get(index))
        children.put(index, tmp)
        updateChildCount()
    }

    private fun moveItemDown(toMove: TypeControl) {
        val children = bound?.getValue(schema) as? JSONArray ?: return
        val index = this.children.indexOf(toMove)
        if (index >= children.length() - 1) return
        val tmp = children.get(index + 1)
        children.put(index + 1, children.get(index))
        children.put(index, tmp)
        updateChildCount()
    }

    private fun validateChildUniqueness() {
        if (!schema.schema.needsUniqueItems()) return
        val children = bound?.getValue(schema) as? JSONArray ?: return

        for (i in 0 until children.length()) {
            for (j in i + 1 until children.length()) {
                if (areSame(children.get(i), children.get(j))) {
                    uniqueItemValidationMessage.set(
                            SimpleValidationMessage(
                                    this.node.value.control as Control,
                                    "Items $i and $j are identical",
                                    Severity.ERROR
                            )
                    )
                    return
                }
            }
        }
        uniqueItemValidationMessage.set(null)
    }

    private fun areSame(a: Any?, b: Any?) = when (a) {
        is JSONObject -> a.similar(b)
        is JSONArray -> a.similar(b)
        else -> a == b
    }
}