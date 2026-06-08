import os

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

replacements = {
    'OrderTelemetryLog': 'OrderTelemetryLogEntity',
    'DomainEvent': 'DomainEventEntity',
    'ProcurementApproval': 'ProcurementApprovalEntity',
    'ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLogEntity': 'ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity',
    'ch.swissqcommerce.backend.domain.event.core.model.DomainEventEntity': 'ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity',
    'ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApprovalEntity': 'ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalEntity'
}

for root, dirs, files in os.walk(domain_root):
    for file in files:
        if file.endswith('.java'):
            path = os.path.join(root, file)
            with open(path, 'r') as f:
                content = f.read()

            changed = False
            for old, new in replacements.items():
                if old in content:
                    content = content.replace(old, new)
                    changed = True

            if changed:
                with open(path, 'w') as f:
                    f.write(content)
                print(f"Patched {path}")
