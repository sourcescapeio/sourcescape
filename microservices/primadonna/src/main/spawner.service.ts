import {
  BadRequestException,
  Inject,
  Injectable,
  InternalServerErrorException,
} from '@nestjs/common';
import {
  CACHE_MANAGER,
} from '@nestjs/cache-manager';
import { parse } from '@typescript-eslint/typescript-estree';
import { Cache } from 'cache-manager';
import { spawn } from 'child_process';
import { ClientProxy, ClientProxyFactory, Transport } from '@nestjs/microservices';
import * as process from 'process';
import { firstValueFrom } from 'rxjs';
import { ts } from '@ts-morph/bootstrap';

const PORT = 3002;

@Injectable()
export class SpawnerService {
  constructor(@Inject(CACHE_MANAGER) private cacheManager: Cache) {}

  async startLanguageServer<T>(
    id: string,
    f: (ClientProxy) => Promise<T>) {
    if (await this.cacheManager.get(id)) {
      throw new BadRequestException('id already exists');
    }

    // spawn a child process
    const child = spawn('node', ['dist/worker.js', PORT.toString()])

    child.on('error', (error) => {
      console.error(`error: ${error.message}`);
    });
    
    child.on('close', (code) => {
      console.log(`child process exited with code ${code}`);
    });    

    await new Promise((resolve) => {
      child.stdout.on('data', (data) => {
        if (data.includes('successfully started')) {
          resolve(true);
        }
      });
    });

    child.stdout.on('data', (data) => {
      console.log(`child:\n${data}`);
    });

    child.stderr.on('data', (data) => {
      console.warn(`child:\n${data}`);
    });

    const clientProxy = ClientProxyFactory.create({
      transport: Transport.TCP,
      options: {
        port: PORT,
      }
    })

    const result = await f(clientProxy)

    if (child.pid) {
      await this.cacheManager.set<number>(id, child.pid, { ttl: 0 });
    } else {
      throw new InternalServerErrorException("improper spawn. no process id")
    }

    return result;
  }

  async stopLanguageServer(id: string) {
    const childId = await this.cacheManager.get<number>(id);

    process.kill(childId, 'SIGINT');

    await this.cacheManager.del(id);
  }

  async languageServerRequest(id: string, filename: string, location: number) {
    const clientProxy = ClientProxyFactory.create({
      transport: Transport.TCP,
      options: {
        port: PORT,
      }
    })

    return firstValueFrom(
      clientProxy.send<{
        definition: ts.DefinitionInfo[],
        typeDefinition: ts.DefinitionInfo[]
      }>({cmd: 'query'}, { filename, location })
    )
  }
}
