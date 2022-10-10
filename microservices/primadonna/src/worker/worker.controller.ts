import { Controller } from '@nestjs/common';
import { MessagePattern } from '@nestjs/microservices';
import { LanguageService } from './language.service';

@Controller()
export class WorkerController {

  constructor(
    private readonly languageService: LanguageService,
  ) {}

  @MessagePattern({ cmd: 'load'})
  async loadFilesIntoMemory(files: {[k: string]: string}) {
    const errors = await this.languageService.createInMemoryProject(files);

    return errors;
  }

  @MessagePattern({ cmd: 'loadDirectory'})
  async loadDirectory(directories: string[]) {
    const errors = await this.languageService.createProjectFromTSConfig(directories);

    return errors;
  }

  @MessagePattern({ cmd: 'query'})
  queryLanguageServer(data: { filename: string, location: number}) {
    const definition = this.languageService.getDefinition(data.filename, data.location);    
    return definition;
  }
}
