import { create } from 'zustand'

export interface Product {
  id: string;
  name: string;
  price: number;
  stock: number;
  stockEast: number;
  category: string;
  emoji: string;
  perishable: boolean;
}

export interface OrderItem {
  id: number | string;
  date: string;
  items: string;
  total: number;
  status: string;
  paymentMethod: string;
}

export type State = any;

export const useStore = create<State>((set) => ({}));
