import re

with open('frontend-host/src/App.tsx', 'r') as f:
    content = f.read()

# Find all remaining useStates
use_states = re.findall(r'  const \[([a-zA-Z0-9_]+), set([a-zA-Z0-9_]+)\] = useState\((.*?)\);', content)

# Generate store.ts
store_ts = """import { create } from 'zustand'

export interface State {
"""

for state, setter, init in use_states:
    store_ts += f"  {state}: any;\n"
    store_ts += f"  set{setter}: (val: any) => void;\n"

store_ts += """}

export const useStore = create<State>((set) => ({
"""

for state, setter, init in use_states:
    store_ts += f"  {state}: {init},\n"
    store_ts += f"  set{setter}: (val) => set({{ {state}: val }}),\n"

store_ts += "}))\n"

with open('frontend-host/src/store.ts', 'a') as f:
    f.write("\n// Auto-generated additions:\n" + store_ts)

# Now remove useStates from App.tsx and add them to the hook destructuring
hook_destructure = ",\n".join([f"    {state}, set{setter}" for state, setter, init in use_states])

# It might be easier to just match and replace them
for state, setter, init in use_states:
    content = re.sub(rf'  const \[{state}, set{setter}\] = useState\(.*?\);\n?', '', content)

content = re.sub(r'  const \{', '  const {\n' + hook_destructure + ',', content, count=1)

with open('frontend-host/src/App.tsx', 'w') as f:
    f.write(content)
