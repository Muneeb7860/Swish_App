import re

with open('frontend-host/src/App.tsx', 'r') as f:
    content = f.read()

# 1. Add strict types for payloads
types = """
interface ErrorBoundaryProps {
  name: string;
  children: React.ReactNode;
}
interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}
class LocalErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
"""
content = re.sub(r'class LocalErrorBoundary extends React.Component \{', types, content)

# 2. Fix use of 'any' in verifyMfeOrigin
verify_mfe = """
const verifyMfeOrigin = (importPromise: Promise<any>, remoteName: string) => {
"""
content = re.sub(r'const verifyMfeOrigin = \(importPromise, remoteName\) => \{', verify_mfe, content)

# 3. Add useStore import
content = content.replace("import * as Lucide from 'lucide-react';", "import * as Lucide from 'lucide-react';\nimport { useStore } from './store';")

# 4. Remove all useState that were moved to store
content = re.sub(r'  const \[activeRole, setActiveRole\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[products, setProducts\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[cart, setCart\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[activeOrder, setActiveOrder\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[orderHistory, setOrderHistory\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[weather, setWeather\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[customerWallet, setCustomerWallet\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[customerPoints, setCustomerPoints\] = useState.*?;\n', '', content)
content = re.sub(r'  const \[riderWallet, setRiderWallet\] = useState.*?;\n', '', content)

# 5. Add useStore hooks
store_hooks = """
  const {
    activeRole, setActiveRole,
    products, setProducts,
    cart, setCart,
    activeOrder, setActiveOrder,
    orderHistory, setOrderHistory,
    weather, setWeather,
    customerWallet, setCustomerWallet,
    customerPoints, setCustomerPoints,
    riderWallet, setRiderWallet
  } = useStore();
"""
content = re.sub(r'  // --- CORE COCKPIT STATES ---.*?\n', '  // --- CORE COCKPIT STATES ---\n' + store_hooks, content)

with open('frontend-host/src/App.tsx', 'w') as f:
    f.write(content)
