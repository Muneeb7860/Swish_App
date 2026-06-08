import os
import re

base_path = "backend/src/main/java/ch/swissqcommerce/backend"

# Fix B2BRestockOrderEntity lombok duplicate annotations
path_b2b = f"{base_path}/domain/wholesaler/adapter/out/persistence/B2BRestockOrderEntity.java"
if os.path.exists(path_b2b):
    with open(path_b2b, "r") as f:
        content = f.read()
    lines = content.splitlines()
    cleaned_lines = []
    seen_lombok = set()
    for line in lines:
        if any(ann in line for ann in ["@Getter", "@Setter", "@NoArgsConstructor", "@AllArgsConstructor", "@Builder", "@Data"]):
            if line.strip() in seen_lombok:
                continue
            seen_lombok.add(line.strip())
        cleaned_lines.append(line)
    with open(path_b2b, "w") as f:
        f.write("\n".join(cleaned_lines))
    print("Cleaned B2BRestockOrderEntity Lombok annotations")

# Fix RiderAcademyCertificateEntity referencing RiderEntity
path_cert = f"{base_path}/domain/enrollment/adapter/out/persistence/RiderAcademyCertificateEntity.java"
if os.path.exists(path_cert):
    with open(path_cert, "r") as f:
        content = f.read()
    content = content.replace("private Rider rider;", "private RiderEntity rider;")
    content = content.replace("import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;", 
                              "import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;")
    with open(path_cert, "w") as f:
        f.write(content)
    print("Fixed RiderAcademyCertificateEntity reference to RiderEntity")

# Fix OrderEntity referencing OrderItemEntity
path_order_entity = f"{base_path}/domain/transaction/adapter/out/persistence/OrderEntity.java"
if os.path.exists(path_order_entity):
    with open(path_order_entity, "r") as f:
        content = f.read()
    content = content.replace("List<OrderItem> items", "List<OrderItemEntity> items")
    content = content.replace("import ch.swissqcommerce.backend.domain.transaction.core.model.OrderItem;",
                              "import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderItemEntity;")
    with open(path_order_entity, "w") as f:
        f.write(content)
    print("Fixed OrderEntity reference to OrderItemEntity")

# Fix PurchaseOrderEntity referencing PurchaseOrderItemEntity
path_po_entity = f"{base_path}/domain/wholesaler/adapter/out/persistence/PurchaseOrderEntity.java"
if os.path.exists(path_po_entity):
    with open(path_po_entity, "r") as f:
        content = f.read()
    content = content.replace("List<PurchaseOrderItem> items", "List<PurchaseOrderItemEntity> items")
    content = content.replace("import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrderItem;",
                              "import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.PurchaseOrderItemEntity;")
    with open(path_po_entity, "w") as f:
        f.write(content)
    print("Fixed PurchaseOrderEntity reference to PurchaseOrderItemEntity")

# Fix PurchaseOrderItemEntity referencing PurchaseOrderEntity
path_poi_entity = f"{base_path}/domain/wholesaler/adapter/out/persistence/PurchaseOrderItemEntity.java"
if os.path.exists(path_poi_entity):
    with open(path_poi_entity, "r") as f:
        content = f.read()
    content = content.replace("private PurchaseOrder purchaseOrder;", "private PurchaseOrderEntity purchaseOrder;")
    content = content.replace("import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;",
                              "import ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.PurchaseOrderEntity;")
    with open(path_poi_entity, "w") as f:
        f.write(content)
    print("Fixed PurchaseOrderItemEntity reference to PurchaseOrderEntity")

# Fix duplicate entity-entity names
duplicate_entity_files = [
    (f"{base_path}/domain/telemetry/adapter/out/persistence/OrderTelemetryLogEntity.java", "OrderTelemetryLogEntityEntity", "OrderTelemetryLogEntity"),
    (f"{base_path}/domain/governance/adapter/out/persistence/ProcurementApprovalEntity.java", "ProcurementApprovalEntityEntity", "ProcurementApprovalEntity"),
    (f"{base_path}/domain/event/adapter/out/persistence/DomainEventEntity.java", "DomainEventEntityEntity", "DomainEventEntity")
]
for f_path, bad_name, good_name in duplicate_entity_files:
    if os.path.exists(f_path):
        with open(f_path, "r") as f:
            content = f.read()
        content = content.replace(bad_name, good_name)
        with open(f_path, "w") as f:
            f.write(content)
        print(f"Renamed {bad_name} to {good_name} in {f_path}")

# Fix EventUseCase / EventPort / EventServiceImpl referencing DomainEventEntity
files_to_fix_event = [
    f"{base_path}/domain/event/port/in/EventUseCase.java",
    f"{base_path}/domain/event/port/out/EventPort.java",
    f"{base_path}/domain/event/core/service/EventServiceImpl.java"
]
for f_path in files_to_fix_event:
    if os.path.exists(f_path):
        with open(f_path, "r") as f:
            content = f.read()
        if "DomainEventEntity" in content and "import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;" not in content:
            content = re.sub(r'package (.*?);', r'package \1;\n\nimport ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;', content, 1)
            with open(f_path, "w") as f:
                f.write(content)
            print(f"Fixed Event import in {f_path}")

# Fix AuthController & InventoryController interface name imports
path_auth_ctrl = f"{base_path}/domain/auth/adapter/in/web/AuthController.java"
if os.path.exists(path_auth_ctrl):
    with open(path_auth_ctrl, "r") as f:
        content = f.read()
    content = content.replace("AuthUseCase", "AuthenticationUseCase")
    content = content.replace("authUseCase", "authenticationUseCase")
    # Clean double package/imports if any
    with open(path_auth_ctrl, "w") as f:
        f.write(content)
    print("Fixed AuthController to use AuthenticationUseCase")

path_inv_ctrl = f"{base_path}/domain/inventory/adapter/in/web/InventoryController.java"
if os.path.exists(path_inv_ctrl):
    with open(path_inv_ctrl, "r") as f:
        content = f.read()
    content = content.replace("InventoryUseCase", "StockManagementUseCase")
    content = content.replace("inventoryUseCase", "stockManagementUseCase")
    content = content.replace("ch.swissqcommerce.backend.domain.inventory.port.in.InventoryUseCase",
                              "ch.swissqcommerce.backend.domain.inventory.port.in.StockManagementUseCase")
    with open(path_inv_ctrl, "w") as f:
        f.write(content)
    print("Fixed InventoryController to use StockManagementUseCase")
