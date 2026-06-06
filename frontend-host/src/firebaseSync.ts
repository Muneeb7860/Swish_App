import { collection, onSnapshot, setDoc, doc, getDocs } from "firebase/firestore";
import { db } from "./firebase";
import { useStore, Product } from "./store";

const INITIAL_PRODUCTS = [
	{
		id: "p1",
		name: "Organic Fresh Milk",
		price: 3.49,
		stock: 12,
		stockEast: 15,
		category: "Dairy & Eggs",
		emoji: "🥛",
		perishable: true,
	},
	{
		id: "p2",
		name: "Chiquita Bananas (1kg)",
		price: 1.99,
		stock: 18,
		stockEast: 20,
		category: "Fruits & Veggies",
		emoji: "🍌",
		perishable: false,
	},
	{
		id: "p3",
		name: "Fresh Hass Avocado (Pair)",
		price: 2.99,
		stock: 8,
		stockEast: 0,
		category: "Fruits & Veggies",
		emoji: "🥑",
		perishable: false,
	},
	{
		id: "p4",
		name: "Coca Cola Zero 6-Pack",
		price: 5.49,
		stock: 15,
		stockEast: 15,
		category: "Snacks & Drinks",
		emoji: "🥤",
		perishable: false,
	},
	{
		id: "p5",
		name: "Whole Wheat Sourdough",
		price: 4.29,
		stock: 6,
		stockEast: 8,
		category: "Bakery",
		emoji: "🍞",
		perishable: false,
	},
	{
		id: "p6",
		name: "Double Chocolate Muffins",
		price: 3.89,
		stock: 2,
		stockEast: 5,
		category: "Bakery",
		emoji: "🧁",
		perishable: false,
	},
	{
		id: "p7",
		name: "Free Range Eggs (Dozen)",
		price: 4.99,
		stock: 10,
		stockEast: 12,
		category: "Dairy & Eggs",
		emoji: "🥚",
		perishable: true,
	},
	{
		id: "p8",
		name: "Potato Chips (Sea Salt)",
		price: 2.49,
		stock: 25,
		stockEast: 30,
		category: "Snacks & Drinks",
		emoji: "🥔",
		perishable: false,
	},
];

export async function syncProductsFromFirebase() {
	const productsRef = collection(db, "products");
	
	// Seed if empty
	const snapshot = await getDocs(productsRef);
	if (snapshot.empty) {
		console.log("Seeding Firebase Firestore with INITIAL_PRODUCTS...");
		for (const p of INITIAL_PRODUCTS) {
			await setDoc(doc(productsRef, p.id), p);
		}
	}

	// Listen for real-time updates
	onSnapshot(productsRef, (snap) => {
		const products: Product[] = [];
		snap.forEach((doc) => {
			products.push(doc.data() as Product);
		});
		
		if (products.length > 0) {
			useStore.getState().setProducts(products);
		}
	});
}
