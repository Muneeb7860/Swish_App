import { useState } from 'react';
import CustomerApp from './components/CustomerApp';
import './index.css';

const MOCK_PRODUCTS = [
  { id: 'p1', name: '[MOCK] Fresh Milk', price: 3.49, stock: 12, category: 'Dairy & Eggs', emoji: '🥛', perishable: true },
  { id: 'p2', name: '[MOCK] Bananas (1kg)', price: 1.99, stock: 18, category: 'Fruits & Veggies', emoji: '🍌', perishable: false }
];

export default function App() {
  const [cart, setCart] = useState([]);
  const [customerWallet, setCustomerWallet] = useState(100.0);
  const [customerPoints, setCustomerPoints] = useState(45);
  const [customerTab, setCustomerTab] = useState('catalog');
  const [profileSubTab, setProfileSubTab] = useState('vip');
  const [esgCheckbox, setEsgCheckbox] = useState(false);
  const [tipAmount, setTipAmount] = useState(0);

  const handleCheckout = (method) => {
    alert(`Mock checkout triggered via: ${method}`);
    setCart([]);
  };

  return (
    <div style={{ padding: '2rem', background: '#0b0f19', minHeight: '100vh', color: '#fff' }}>
      <h2>Customer MFE (Standalone Dev Preview)</h2>
      <CustomerApp {...({} as any)}
        products={MOCK_PRODUCTS}
        cart={cart}
        setCart={setCart}
        customerWallet={customerWallet}
        setCustomerWallet={setCustomerWallet}
        customerPoints={customerPoints}
        setCustomerPoints={setCustomerPoints}
        customerTab={customerTab}
        setCustomerTab={setCustomerTab}
        profileSubTab={profileSubTab}
        setProfileSubTab={setProfileSubTab}
        savedAddresses={[]}
        savedCards={[]}
        favorites={[]}
        vipMember={true}
        vouchers={[]}
        customerTrustScore={100}
        gdprTokenProbation={false}
        handleGdprPurge={() => alert('Mock purge')}
        orderHistory={[]}
        esgCheckbox={esgCheckbox}
        setEsgCheckbox={setEsgCheckbox}
        tipAmount={tipAmount}
        setTipAmount={setTipAmount}
        handleCheckout={handleCheckout}
        activeOrder={null}
        generateCertificate={(role) => alert(`Mock Certificate for: ${role}`)}
      />
    </div>
  );
}
