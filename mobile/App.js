import React, { useState } from 'react';
import { StyleSheet, View, Text, TouchableOpacity, SafeAreaView, StatusBar } from 'react-native';
import CustomerScreen from './src/screens/CustomerScreen';
import PickerScreen from './src/screens/PickerScreen';
import RiderScreen from './src/screens/RiderScreen';

export default function App() {
  const [activeCockpit, setActiveCockpit] = useState('customer'); // Default to Customer Cockpit first
  
  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#070a13" />
      
      {/* Top Cockpit Role Switcher */}
      <View style={styles.topSelector}>
        <TouchableOpacity 
          style={[styles.selectorBtn, activeCockpit === 'customer' && styles.selectorBtnActiveCustomer]}
          onPress={() => setActiveCockpit('customer')}
        >
          <Text style={[styles.selectorText, activeCockpit === 'customer' && styles.selectorTextActiveCustomer]}>
            🛍️ CUSTOMER
          </Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.selectorBtn, activeCockpit === 'rider' && styles.selectorBtnActiveRider]}
          onPress={() => setActiveCockpit('rider')}
        >
          <Text style={[styles.selectorText, activeCockpit === 'rider' && styles.selectorTextActiveRider]}>
            🏍️ RIDER
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity 
          style={[styles.selectorBtn, activeCockpit === 'picker' && styles.selectorBtnActivePicker]}
          onPress={() => setActiveCockpit('picker')}
        >
          <Text style={[styles.selectorText, activeCockpit === 'picker' && styles.selectorTextActivePicker]}>
            📦 PICKER
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.screenWrapper}>
        {activeCockpit === 'customer' && <CustomerScreen />}
        {activeCockpit === 'rider' && <RiderScreen />}
        {activeCockpit === 'picker' && <PickerScreen />}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#070a13',
  },
  topSelector: {
    flexDirection: 'row',
    backgroundColor: '#070a13',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    paddingVertical: 10,
    paddingHorizontal: 12,
    justifyContent: 'center',
    gap: 8,
  },
  selectorBtn: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
  },
  selectorBtnActiveCustomer: {
    borderColor: '#10b981',
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
  },
  selectorBtnActiveRider: {
    borderColor: '#f59e0b',
    backgroundColor: 'rgba(245, 158, 11, 0.15)',
  },
  selectorBtnActivePicker: {
    borderColor: '#3b82f6',
    backgroundColor: 'rgba(59, 130, 246, 0.15)',
  },
  selectorText: {
    fontSize: 9,
    fontWeight: '800',
    color: '#94a3b8',
    letterSpacing: 0.5,
  },
  selectorTextActiveCustomer: {
    color: '#10b981',
  },
  selectorTextActiveRider: {
    color: '#f59e0b',
  },
  selectorTextActivePicker: {
    color: '#3b82f6',
  },
  screenWrapper: {
    flex: 1,
  },
});
