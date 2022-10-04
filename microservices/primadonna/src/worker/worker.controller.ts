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
    await this.languageService.createInMemoryProject(files);

    return true
  }

  @MessagePattern({ cmd: 'query'})
  queryLanguageServer(data: { filename: string, location: number}) {
    const definition = this.languageService.getDefinition(data.filename, data.location);    
    return definition;
  }
}
