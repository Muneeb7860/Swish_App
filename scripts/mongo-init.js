// Create dedicated application user for Swish App's swiss_olap collection
db = db.getSiblingDB("swiss_olap");

if (!db.getUser("swiss_app")) {
	db.createUser({
		user: "swiss_app",
		pwd: "swiss_app_password",
		roles: [{ role: "readWrite", db: "swiss_olap" }],
	});
	print("MongoDB user 'swiss_app' created successfully.");
} else {
	print("MongoDB user 'swiss_app' already exists.");
}
