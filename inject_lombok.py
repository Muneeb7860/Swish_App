import os
import re

files_to_fix = [
    'backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/core/model/B2BRestockOrder.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/core/model/Wholesaler.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/core/model/PurchaseOrder.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/core/model/PurchaseOrderItem.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/auth/core/model/UserAccount.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/auth/core/model/Session.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/dispatch/core/model/ActiveShipment.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/dispatch/core/model/RouteCoordinates.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/agent/port/out/LlmResponse.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/reward/core/model/RewardPoints.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/feedback/core/model/Feedback.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/transaction/core/model/Order.java',
    'backend/src/main/java/ch/swissqcommerce/backend/domain/transaction/core/model/OrderItem.java',
]

def add_lombok(filepath):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return
        
    with open(filepath, 'r') as f:
        content = f.read()

    # ensure imports exist
    if 'import lombok.Data;' not in content:
        content = re.sub(r'(package .*;)', r'\1\n\nimport lombok.Data;\nimport lombok.Builder;\nimport lombok.NoArgsConstructor;\nimport lombok.AllArgsConstructor;\n', content)

    # ensure annotations exist
    # find the class declaration
    if '@Data' not in content:
        content = re.sub(r'(public class [A-Za-z0-9_]+)', r'@Data\n@Builder\n@NoArgsConstructor\n@AllArgsConstructor\n\1', content)

    with open(filepath, 'w') as f:
        f.write(content)

for file in files_to_fix:
    add_lombok(file)

print("Lombok annotations injected.")
