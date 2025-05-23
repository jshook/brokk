; Top-level class (non-exported)
((class_declaration
  name: (identifier) @class.name) @class.definition)

; Top-level function (non-exported)
((function_declaration
  name: (identifier) @function.name) @function.definition)

; Top-level const/let/var MyComponent = () => { ... }
((lexical_declaration
  (variable_declarator
    name: (identifier) @function.name
    value: ((arrow_function) @function.definition))))

; Top-level non-exported const/let/var variable assignment
; ((lexical_declaration
;  (variable_declarator
;    name: (identifier) @field.name
;    value: _ ; Ensures it's an assignment with a value
;  )
; ) @field.definition) ; Capture the entire lexical_declaration as the definition

; Class method
; Captures method_definition within a class_body. Name can be property_identifier or identifier.
(
  (class_declaration
    body: (class_body
            (method_definition
              name: [(property_identifier) (identifier)] @function.name
            ) @function.definition
          )
  )
)

; Top-level non-exported const/let/var variable assignment
; Catches 'const x = 1;', 'let y = "foo";', 'var z = {};' etc.
; but not 'const F = () => ...' or 'const C = class ...'
[
  (lexical_declaration
    (variable_declarator
      name: (identifier) @field.name
      value: [
        (string)
        (template_string)
        (number)
        (regex)
        (true)
        (false)
        (null)
        (undefined)
        (object)
        (array)
        (identifier)
        (binary_expression)
        (unary_expression)
        (member_expression)
        (subscript_expression)
        (call_expression)
      ]
    ) @field.definition
  )
  (variable_declaration
    (variable_declarator
      name: (identifier) @field.name
      value: [
        (string)
        (template_string)
        (number)
        (regex)
        (true)
        (false)
        (null)
        (undefined)
        (object)
        (array)
        (identifier)
        (binary_expression)
        (unary_expression)
        (member_expression)
        (subscript_expression)
        (call_expression)
      ]
    ) @field.definition
  )
]

; Exported top-level const/let/var variable assignment
; Catches 'export const x = 1;' etc.
(
  (export_statement
    declaration: [
      (lexical_declaration
        (variable_declarator
          name: (identifier) @field.name
          value: [
            (string)
            (template_string)
            (number)
            (regex)
            (true)
            (false)
            (null)
            (undefined)
            (object)
            (array)
            (identifier)
            (binary_expression)
            (unary_expression)
            (member_expression)
            (subscript_expression)
            (call_expression)
          ]
        ) @field.definition
      )
      (variable_declaration
        (variable_declarator
          name: (identifier) @field.name
          value: [
            (string)
            (template_string)
            (number)
            (regex)
            (true)
            (false)
            (null)
            (undefined)
            (object)
            (array)
            (identifier)
            (binary_expression)
            (unary_expression)
            (member_expression)
            (subscript_expression)
            (call_expression)
          ]
        ) @field.definition
      )
    ]
  )
)

; Exported top-level class
((export_statement
  declaration: (class_declaration
    name: (identifier) @class.name
  )
) @class.definition)

; Exported top-level function
((export_statement
  declaration: (function_declaration
    name: (identifier) @function.name
  )
) @function.definition)

; Ignore decorators / modifiers for now
