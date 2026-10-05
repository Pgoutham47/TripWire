`litert-torch` 0.9.4 imports `tensorflow.lite.python.schema_py_generated` but does not depend on
TensorFlow. Installing TensorFlow beside `litert-converter` aborts with
"Option 'info-output-file' registered more than once" (both link LLVM). This shim maps the one
import to `ai_edge_litert.schema_py_generated`, which is the same generated flatbuffer schema.
Remove it when litert-torch drops the import.
