import {Command, flags} from '@oclif/command'
import * as _ from 'lodash';
import config from '../config';
import { stopAll, remapYAMLTier } from '../lib/docker';

export default class Down extends Command {
  static description = 'Shuts down running SourceScape containers.'

  static examples = [
    `$ sourcescape down`,
  ]

  async run() {
    const {flags} = this.parse(Down)

    const flattened = _.flatten((config.services as any[]).map((items: any) => {
      return remapYAMLTier(items);
    }));

    await stopAll(flattened, this.log, true, false); // destroy = false

    this.exit(0)
  }
}
