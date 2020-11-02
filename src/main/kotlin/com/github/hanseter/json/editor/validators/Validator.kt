package com.github.hanseter.json.editor.validators

import com.github.hanseter.json.editor.actions.ActionTargetSelector
import com.github.hanseter.json.editor.types.TypeModel

/**
 * A validator can be applied to different parts of the displayed objects. The validation returns a list of errors.
 */
interface Validator {
    /**
     * A selector which determines whether the validator shall be applied to the current data.
     */
    val selector: ActionTargetSelector

    /**
     * Returns an error message if validation fails.
     */
    fun validate(model: TypeModel<*,*>): List<String>
}