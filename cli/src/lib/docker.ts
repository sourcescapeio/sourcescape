import { Options, Docker } from 'docker-cli-js';
import DockerAlt from 'dockerode';
import { compact, difference, find, flatten } from 'lodash';
import { SOURCESCAPE_DIR, getMinorVersion } from './data';

const docker = new Docker(new Options(
  undefined,
  undefined,
  false, // echo
  undefined,
));

const dockerAlt = new DockerAlt();

const NETWORK_NAME = 'sourcescape';


interface ServiceConfig {
  image: string;
  name: string; // used for container name and host name
  //
  base: boolean | null;
  ipc: boolean | null;
  platform: string | null;
  script: boolean | null;
  environment: string[] | null;
  data_volume: string | null;
  external_volume: string | null;
  command: string | null;
  ports: string[] | null;
  complete: string | null;
}

interface ContainerState {
  id: string;
  name: string;
}

export async function getImages() {
  const images = await docker.command('images');

  return images.images;
}

export async function pullImage(image: string, tag: string) {
  return await docker.command(`pull ${image}:${tag}`);
}

async function getContainers(args: string): Promise<ContainerState[]> {
  const ret = await docker.command(`ps ${args}`);
  return ret.containerList.map((c: any) => ({
    name: c.names,
    id: c['container id'],
  }));
}

function constructCommand(item: ServiceConfig, directories: string[], minorVersion: string): string {
  const { name, image } = item;

  const imageWithVersion = compact([
    image,
    !item.base && `:${minorVersion}`,
  ]).join('');

  const args = flatten(compact([
    !item.script && '-d',
    `--name ${name}`,
    `--network ${NETWORK_NAME}`,
    `--hostname ${name}`,
    '--privileged',
    item.platform && `--platform ${item.platform}`,
    item.ipc && '--ipc host',
    item.base && '--pull always',
    item.data_volume && `--volume ${SOURCESCAPE_DIR}:${item.data_volume}`,
    item.external_volume && directories.map((d) => (`--volume ${d}:${item.external_volume}/${d}:cached`)),
    item.environment && item.environment.map((e) => (`--env ${e}`)),
    item.ports && item.ports.map((p) => (`-p ${p}`)),
  ]));

  return `run ${args.join(' ')} ${imageWithVersion} ${item.command || ''}`;
}

export async function ensureNetwork() {
  const networks = await docker.command('network ls').then((n) => (n.network));
  const maybeNetwork = find(networks, ((n) => (n.name === NETWORK_NAME)))

  if (maybeNetwork) {
    console.warn(`Network ${NETWORK_NAME} exists.`);
  } else {
    await docker.command(`network create ${NETWORK_NAME}`);
  }
}

export async function statusTier(items: ServiceConfig[], log: Function) {
  const runningContainers = await getContainers('');
  const stoppedContainers = await getContainers('-a');  

  items.map((item) => {
    const { name } = item;
    const maybeRunning = find(runningContainers, (c) => (c.name === name));
    if (maybeRunning) {
      log(`RUNNING: ${maybeRunning.id} ${name}`);
      return null;
    }

    const maybeStopped = find(stoppedContainers, (c) => (c.name === name));
    if (maybeStopped) {
      log(`STOPPED: ${maybeStopped.id} ${name}`);
      return null;
    }

    log(`NOT PRESENT: ${name}`);
  });
}

export async function stopAll(items: ServiceConfig[], log: Function, downBase: boolean, destroy: boolean) {
  const runningContainers = await getContainers('');
  const stoppedContainers = await getContainers('-a')

  const work = items.map(async (item) => {
    const { name } = item;
    // skip base
    if (item.base && !downBase) {
      return Promise.resolve(true);
    }

    const maybeRunning = find(runningContainers, (c) => (c.name === name));
    if (maybeRunning && !destroy) {
      log(`STOPPING ${name}`);
      await docker.command(`stop ${name}`);
      return Promise.resolve(true);
    }

    if(maybeRunning && destroy) {
      log(`STOPPING AND DELETING ${name}`);
      await docker.command(`stop ${name}`);
      await docker.command(`rm -v ${name}`);
      return Promise.resolve(true);
    }

    const maybeStopped = find(stoppedContainers, (c) => (c.name === name));
    if (maybeStopped && destroy) {
      log(`DELETING ${name}`)
      await docker.command(`rm -v ${name}`);
      return Promise.resolve(true);;
    }

    return Promise.resolve(true);
  })

  return Promise.all(work);
}

export function remapYAMLTier(items: any): ServiceConfig[] {
  return Object.keys(items).map((k) => {
    const v = items[k];
    return {
      image: v.image,
      name: k,
      //
      base: v.base,
      platform: v.platform,
      ipc: v.ipc,
      script: v.script,
      environment: v.environment,
      data_volume: v.data_volume,
      external_volume: v.external_volume,
      command: v.command,
      ports: v.ports,
      complete: v.complete,
    }
  });  
}

async function watchContainer(containerId: string, item: ServiceConfig, log: Function) {
  if(item.complete) {
    log(`ATTACHING LOG ${item.name}`);
    const logContainer = dockerAlt.getContainer(containerId);
    const logStream = await logContainer.attach({follow: true, stream: true, stdout: true, stderr: true});

    return new Promise((resolve, reject) => {
      logStream.on('data', (chunk: any) => {
        const logChunk = chunk.toString();
        if (logChunk.indexOf(item.complete) !== -1) {
          log(`COMPLETED ${item.name}`);
          (logStream as any).destroy(); // not documented but works
          resolve(true);
        }
        // else {
        //   log(logChunk);
        // }
      });
    });
  } else {
    log(`COMPLETED ${item.name}`);
    return Promise.resolve(containerId);
  }  
}

export async function ensureTier(items: ServiceConfig[], directories: string[], minorVersion: string, log: Function) {
  const runningContainers = await getContainers('');
  const stoppedContainers = await getContainers('--filter status=exited')

  const tierWork = items.map(async (item) => {
    const { name } = item;
    // already running
    const maybeRunning = find(runningContainers, (c) => (c.name === name));
    if (maybeRunning) {
      const containerId = maybeRunning.id
      log(`ALREADY RUNNING: ${name} ${containerId}`);
      return Promise.resolve(maybeRunning);
    }

    // Stopped
    const maybeStopped = find(stoppedContainers, (c) => (c.name === name));
    if (maybeStopped) {
      const containerId = maybeStopped.id;

      // skip restart for script
      if (item.script) {
        log(`SKIPPING SCRIPT: ${name}`);
        return false;
      }

      if (directories.length > 0 && item.external_volume) {
        const inspected = await docker.command(`inspect ${containerId}`);
        const containerSources = inspected.object[0].Mounts.map((m: any) => (m.Source.replace('/host_mnt', '')));
        const missing = difference(directories, containerSources);
        if (missing.length > 0) {
          log("CONTAINERS ARE ALREADY RUNNING WITH DIFFERENT MOUNTS");
          log(`MISSING MOUNTS: ${missing}`);
          log('Need to destroy with `sourcescape clean` and recreate');
          process.exit(1);
        }
      }
      
      await docker.command(`start ${containerId}`);
      log(`RESTARTED: ${name} ${containerId}`);
      return watchContainer(containerId, item, log);
    }

    // New
    const command = constructCommand(item, directories, minorVersion);
    log(`CREATING: ${command}`);
    const containerCreate = await docker.command(command);
    return watchContainer(containerCreate.containerId, item, log);
  });

  return Promise.all(tierWork);
}

export async function wipeImages(items: ServiceConfig[]) {

  const minorVersion = getMinorVersion();

  const deleteWork = items.filter((i) => (!i.base)).map((i) => {
    console.warn(`Deleting image ${i.image}:${minorVersion}`);
    return docker.command(`rmi ${i.image}:${minorVersion}`).catch((e) => {
      const { stderr } = e;
      if (stderr.indexOf(`Error: No such image: ${i.image}:${minorVersion}`) === 0) {
        // swallow
        return null;
      } else {
        throw e;
      }
    });
  });

  return Promise.all(deleteWork);
}
