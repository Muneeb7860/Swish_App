import os
import re

domain_root = 'backend/src/main/java/ch/swissqcommerce/backend/domain'

# List of models that we moved to *Entity in JPA
models_to_fix = [
    'Order', 'OrderItem', 'JournalEntry', 'LedgerLine',
    'Rider', 'RiderAcademyCertificate', 'OnboardingApplication',
    'B2BRestockOrder', 'Wholesaler', 'Payment',
    'GearScan', 'VehicleConfig', 'Feedback',
    'RewardPoints', 'CustomerLoyalty', 'ActiveShipment'
]

for root, dirs, files in os.walk(domain_root):
    for file in files:
        if file.endswith('.java'):
            path = os.path.join(root, file)
            with open(path, 'r') as f:
                content = f.read()

            changed = False
            for model in models_to_fix:
                # Fix JpaRepository<Model, ID> -> JpaRepository<ModelEntity, ID>
                if f'JpaRepository<{model},' in content:
                    content = content.replace(f'JpaRepository<{model},', f'JpaRepository<{model}Entity,')
                    changed = True
                
                # Fix CrudRepository if any
                if f'CrudRepository<{model},' in content:
                    content = content.replace(f'CrudRepository<{model},', f'CrudRepository<{model}Entity,')
                    changed = True

                # If it's a repository or persistence adapter, we need to import the Entity
                if 'adapter/out/persistence' in path and f'import ch.swissqcommerce.backend.domain.' in content:
                    # Crude but effective: just import the entity version as well
                    if f'{model}Entity' not in content:
                        import_stmt = re.search(r'import ch\.swissqcommerce\.backend\.domain\..*\.core\.model\.' + model + ';', content)
                        if import_stmt:
                            new_import = import_stmt.group(0).replace('.core.model.', '.adapter.out.persistence.').replace(model + ';', model + 'Entity;')
                            content = content.replace(import_stmt.group(0), import_stmt.group(0) + '\n' + new_import)
                            changed = True

            if changed:
                with open(path, 'w') as f:
                    f.write(content)
                print(f"Fixed Repositories in {path}")
