#!/usr/bin/env node
const express = require("express");
const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const {
	StdioServerTransport,
} = require("@modelcontextprotocol/sdk/server/stdio.js");
const {
	CallToolRequestSchema,
	ListToolsRequestSchema,
} = require("@modelcontextprotocol/sdk/types.js");

// 10 FMCG Barcodes Competitor Pricing database
const COMPETITOR_PRICES = {
	7613035449626: {
		barcode: "7613035449626",
		competitor: "Coop",
		competitorPrice: 3.95,
		currency: "CHF",
	},
	7613036239110: {
		barcode: "7613036239110",
		competitor: "Migros",
		competitorPrice: 3.8,
		currency: "CHF",
	},
	3017670997017: {
		barcode: "3017670997017",
		competitor: "Denner",
		competitorPrice: 3.4,
		currency: "CHF",
	},
	5449000000996: {
		barcode: "5449000000996",
		competitor: "Coop",
		competitorPrice: 1.2,
		currency: "CHF",
	},
	7610200420188: {
		barcode: "7610200420188",
		competitor: "Migros",
		competitorPrice: 0.95,
		currency: "CHF",
	},
	7613035939530: {
		barcode: "7613035939530",
		competitor: "Denner",
		competitorPrice: 5.6,
		currency: "CHF",
	},
	7622210888015: {
		barcode: "7622210888015",
		competitor: "Coop",
		competitorPrice: 1.95,
		currency: "CHF",
	},
	7613034926869: {
		barcode: "7613034926869",
		competitor: "Migros",
		competitorPrice: 2.3,
		currency: "CHF",
	},
	4005900251786: {
		barcode: "4005900251786",
		competitor: "Coop",
		competitorPrice: 1.85,
		currency: "CHF",
	},
	7610070001533: {
		barcode: "7610070001533",
		competitor: "Migros",
		competitorPrice: 3.1,
		currency: "CHF",
	},
};

// Start REST server
const app = express();
const PORT = process.env.PORT || 8089;

app.get("/api/v1/competitor/price/:barcode", (req, res) => {
	const barcode = req.params.barcode;
	const priceData = COMPETITOR_PRICES[barcode];
	if (priceData) {
		res.json(priceData);
	} else {
		// Fallback for unknown items
		res.status(404).json({ error: "Product not found in competitor catalogs" });
	}
});

const server = app.listen(PORT, () => {
	console.warn(`Competitor REST Pricing API running on port ${PORT}`);
});

// Setup MCP stdio Server
const mcpServer = new Server(
	{
		name: "competitor-supermarket-pricing",
		version: "1.0.0",
	},
	{
		capabilities: {
			tools: {},
		},
	},
);

mcpServer.setRequestHandler(ListToolsRequestSchema, async () => {
	return {
		tools: [
			{
				name: "get_competitor_price",
				description:
					"Retrieve active competitor price for a product using its barcode.",
				inputSchema: {
					type: "object",
					properties: {
						barcode: {
							type: "string",
							description: "The product EAN/UPC barcode.",
						},
					},
					required: ["barcode"],
				},
			},
		],
	};
});

mcpServer.setRequestHandler(CallToolRequestSchema, async (request) => {
	if (request.params.name === "get_competitor_price") {
		const barcode = request.params.arguments.barcode;
		const priceData = COMPETITOR_PRICES[barcode];
		if (priceData) {
			return {
				content: [
					{
						type: "text",
						text: JSON.stringify(priceData, null, 2),
					},
				],
			};
		} else {
			return {
				content: [
					{
						type: "text",
						text: JSON.stringify({ error: `Barcode ${barcode} not found.` }),
					},
				],
				isError: true,
			};
		}
	}
	throw new Error(`Tool not found: ${request.params.name}`);
});

// Connect to stdio
async function runMcp() {
	const transport = new StdioServerTransport();
	await mcpServer.connect(transport);
	console.warn("Competitor Supermarket Pricing MCP server running on stdio");
}

runMcp().catch((err) => {
	console.error("MCP setup failed:", err);
});
