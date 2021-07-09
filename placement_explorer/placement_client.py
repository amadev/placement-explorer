import os_sdk_light as osl



def collect(cloud):
    client = osl.get_client(
        cloud=cloud,
        service='placement',
        schema=osl.schema('placement.yaml'))
    providers = client.resource_providers.list()['resource_providers']
    for p in providers:
        inventories[p['uuid']] = client.resource_providers.get_inventories()
        usages[p['uuid']] = client.resource_providers.get_usage()
    return { }
