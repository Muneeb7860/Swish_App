import os
import re

def find_ports(root_dir):
    ports = []
    for dirpath, _, filenames in os.walk(root_dir):
        if 'port/out' in dirpath:
            for f in filenames:
                if f.endswith('Port.java'):
                    ports.append(os.path.join(dirpath, f))
    return ports

def find_implementations(root_dir, port_name):
    # Search all java files for "implements.*port_name"
    for dirpath, _, filenames in os.walk(root_dir):
        for f in filenames:
            if f.endswith('.java'):
                with open(os.path.join(dirpath, f), 'r') as file:
                    content = file.read()
                    if re.search(r'implements\s+([^,]+,)*\s*' + port_name + r'\b', content):
                        return True
    return False

def generate_dummy(port_path):
    with open(port_path, 'r') as f:
        lines = f.readlines()
    
    package_line = next(line for line in lines if line.startswith('package'))
    package_name = package_line.split(' ')[1].strip().replace(';', '')
    
    port_name = os.path.basename(port_path).replace('.java', '')
    dummy_name = port_name.replace('Port', 'DummyAdapter')
    
    dummy_package = package_name.replace('.port.out', '.adapter.out.dummy')
    dummy_dir = os.path.join(os.path.dirname(port_path).replace('/port/out', '/adapter/out/dummy'))
    
    os.makedirs(dummy_dir, exist_ok=True)
    dummy_path = os.path.join(dummy_dir, f"{dummy_name}.java")
    
    # We will just write a class that implements the interface. But we need to implement methods.
    # To avoid writing a complex parser, what if we use Mockito to provide a bean?
    # Actually, a better way is to provide a single @Configuration class that defines @Bean methods using Mockito.mock() for all missing ports!
    pass

if __name__ == '__main__':
    root = 'src/main/java'
    ports = find_ports(root)
    missing = []
    for p in ports:
        name = os.path.basename(p).replace('.java', '')
        if not find_implementations(root, name):
            missing.append((p, name))
            
    print("Missing implementations for:")
    for p, name in missing:
        # get fully qualified name
        with open(p, 'r') as f:
            pkg = next(line for line in f if line.startswith('package')).split()[1].replace(';', '')
            fqn = f"{pkg}.{name}"
            print(fqn)
